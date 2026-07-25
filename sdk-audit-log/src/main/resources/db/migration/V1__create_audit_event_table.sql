-- Reference migration for audit_event table.
-- Copy this file to your application's migration directory and adapt column types to your database.
-- SQL Server variant shown below; adjust NVARCHAR(MAX) → TEXT for PostgreSQL/MySQL.

CREATE TABLE audit_event (
    id               UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    event_type       NVARCHAR(100)    NOT NULL,
    entity_id        NVARCHAR(255)    NULL,
    action           NVARCHAR(50)     NOT NULL,
    user_id          NVARCHAR(255)    NULL,
    role             NVARCHAR(50)     NULL,
    resource         NVARCHAR(255)    NULL,
    resource_id      NVARCHAR(255)    NULL,
    http_method      NVARCHAR(10)     NULL,
    http_status      INT              NULL,
    ip_address       NVARCHAR(50)     NULL,
    user_agent       NVARCHAR(1000)   NULL,
    request_body     NVARCHAR(MAX)    NULL,
    response_summary NVARCHAR(MAX)    NULL,
    success          BIT              NULL,
    error_message    NVARCHAR(MAX)    NULL,
    duration_ms      INT              NULL,
    metadata         NVARCHAR(MAX)    NULL,
    source_service   NVARCHAR(100)    NULL,
    correlation_id   UNIQUEIDENTIFIER NULL,
    event_created_at DATETIMEOFFSET   NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (id)
);

CREATE INDEX idx_audit_event_type       ON audit_event (event_type);
CREATE INDEX idx_audit_event_action     ON audit_event (action);
CREATE INDEX idx_audit_event_user_id    ON audit_event (user_id);
CREATE INDEX idx_audit_event_created_at ON audit_event (event_created_at);
CREATE INDEX idx_audit_event_corr_id    ON audit_event (correlation_id);
