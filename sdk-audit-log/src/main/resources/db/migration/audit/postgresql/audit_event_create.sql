-- Reference DDL for the audit_event table, PostgreSQL.
-- Copy it into the application's own migrations; the SDK never runs it. The file name has no
-- Flyway version prefix on purpose, so a classpath:db/migration scan ignores it.
-- Requires PostgreSQL 13 or later for gen_random_uuid(); on 12 or earlier run
-- CREATE EXTENSION IF NOT EXISTS pgcrypto; first.

CREATE TABLE audit_event (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    event_type       VARCHAR(100) NOT NULL,
    entity_id        VARCHAR(255) NULL,
    action           VARCHAR(50)  NOT NULL,
    user_id          VARCHAR(255) NULL,
    role             VARCHAR(50)  NULL,
    resource         VARCHAR(255) NULL,
    resource_id      VARCHAR(255) NULL,
    http_method      VARCHAR(10)  NULL,
    http_status      INTEGER      NULL,
    ip_address       VARCHAR(50)  NULL,
    user_agent       VARCHAR(1000) NULL,
    request_body     TEXT         NULL,
    response_summary TEXT         NULL,
    success          BOOLEAN      NULL,
    error_message    TEXT         NULL,
    duration_ms      INTEGER      NULL,
    metadata         TEXT         NULL,
    source_service   VARCHAR(100) NULL,
    correlation_id   UUID         NULL,
    event_created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (id)
);

CREATE INDEX idx_audit_event_type       ON audit_event (event_type);
CREATE INDEX idx_audit_event_action     ON audit_event (action);
CREATE INDEX idx_audit_event_user_id    ON audit_event (user_id);
CREATE INDEX idx_audit_event_created_at ON audit_event (event_created_at);
CREATE INDEX idx_audit_event_corr_id    ON audit_event (correlation_id);
