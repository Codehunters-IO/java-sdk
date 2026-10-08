CREATE TABLE outbox_message (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    seq BIGINT GENERATED ALWAYS AS IDENTITY,
    origin VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255),
    topic VARCHAR(255) NOT NULL,
    message_key VARCHAR(255),
    payload VARCHAR NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    attempts INT DEFAULT 0,
    error VARCHAR,
    occurred_at TIMESTAMP,
    next_attempt_at TIMESTAMP,
    published_at TIMESTAMP,
    claimed_at TIMESTAMP,
    claimed_by VARCHAR(255),
    send_started_at TIMESTAMP,
    created_by VARCHAR(255),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_by VARCHAR(255),
    last_modified_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_outbox_origin_status_occurred
    ON outbox_message(origin, status, occurred_at ASC);

CREATE INDEX idx_outbox_origin_next_attempt
    ON outbox_message(origin, status, next_attempt_at);

CREATE INDEX idx_outbox_claim_recovery
    ON outbox_message(status, claimed_at);

CREATE INDEX idx_outbox_key_order
    ON outbox_message(origin, topic, message_key, seq);
