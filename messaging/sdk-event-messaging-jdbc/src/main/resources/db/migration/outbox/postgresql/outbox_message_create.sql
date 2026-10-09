--liquibase formatted sql

-- Reference DDL: copy into your service changelog and adapt changeset author/id; never include it from the jar.
-- An SDK-owned include would change checksums on every SDK upgrade.
-- seq records insertion order; the claim orders by it and blocks a row while an earlier row with the
-- same (origin, topic, message_key) is still PENDING, IN_FLIGHT or FAILED.
-- Timestamps are TIMESTAMP without time zone: the adapter binds LocalDateTime from a UTC clock.
-- send_started_at marks a claimed row whose publish began; a reclaim charges an attempt only then.
-- traceparent stores the W3C trace context of the work that created the row (00-<trace>-<span>-<flags>,
-- 55 characters); the publisher sends it as the traceparent broker header.
-- Tables created by 6.x: run postgresql/outbox_message_add_send_started_at.sql, then
-- postgresql/outbox_message_add_traceparent.sql. Tables created by 7.x or 8.x: run the latter only.

--changeset codehunters-sdk:outbox-message-create
CREATE TABLE outbox_message (
    id UUID PRIMARY KEY,
    seq BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL UNIQUE,
    origin VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255),
    topic VARCHAR(255) NOT NULL,
    message_key VARCHAR(255),
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    error VARCHAR(512),
    occurred_at TIMESTAMP NOT NULL,
    next_attempt_at TIMESTAMP,
    published_at TIMESTAMP,
    claimed_at TIMESTAMP,
    claimed_by VARCHAR(255),
    send_started_at TIMESTAMP,
    traceparent VARCHAR(55)
);
--rollback DROP TABLE outbox_message;

--changeset codehunters-sdk:outbox-message-indexes
CREATE INDEX idx_outbox_claimable ON outbox_message (origin, seq)
    WHERE status IN ('PENDING', 'IN_FLIGHT');
CREATE INDEX idx_outbox_key_order ON outbox_message (origin, topic, message_key, seq)
    WHERE status IN ('PENDING', 'IN_FLIGHT', 'FAILED');
CREATE INDEX idx_outbox_published ON outbox_message (published_at)
    WHERE status = 'PUBLISHED';
--rollback DROP INDEX idx_outbox_published;
--rollback DROP INDEX idx_outbox_key_order;
--rollback DROP INDEX idx_outbox_claimable;

--changeset codehunters-sdk:shedlock-create
CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
--rollback DROP TABLE shedlock;
