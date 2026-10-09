-- Reference migration: Final schema for outbox_message and shedlock
-- Copy to your ms changelog directory and adapt changeset author/id
-- Existing installs created before SDK 6.0.0: run sqlserver/outbox_message_add_seq.sql, then
-- sqlserver/outbox_message_add_send_started_at.sql; tables created by 6.x need only the latter

-- changeset sdk:outbox-create-1
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'outbox_message')
BEGIN
    CREATE TABLE outbox_message (
        id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
        seq BIGINT IDENTITY(1,1) NOT NULL,
        origin NVARCHAR(255) NOT NULL,
        routing_key NVARCHAR(255),
        topic NVARCHAR(255) NOT NULL,
        message_key NVARCHAR(255),
        payload NVARCHAR(MAX) NOT NULL,
        status NVARCHAR(50) NOT NULL DEFAULT 'PENDING',
        attempts INT DEFAULT 0,
        error NVARCHAR(MAX),
        occurred_at DATETIME2,
        next_attempt_at DATETIME2,
        published_at DATETIME2,
        claimed_at DATETIME2 NULL,
        claimed_by NVARCHAR(255) NULL,
        send_started_at DATETIME2 NULL,
        created_by NVARCHAR(255),
        created_date DATETIME2 DEFAULT GETDATE(),
        modified_by NVARCHAR(255),
        last_modified_date DATETIME2 DEFAULT GETDATE()
    );
END

-- changeset sdk:outbox-create-2
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_outbox_origin_status_occurred')
BEGIN
    CREATE INDEX idx_outbox_origin_status_occurred
        ON outbox_message(origin, status, occurred_at ASC);
END

-- changeset sdk:outbox-create-3
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_outbox_origin_next_attempt')
BEGIN
    CREATE INDEX idx_outbox_origin_next_attempt
        ON outbox_message(origin, status, next_attempt_at);
END

-- changeset sdk:outbox-create-4
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_outbox_claim_recovery')
BEGIN
    CREATE INDEX idx_outbox_claim_recovery
        ON outbox_message(status, claimed_at)
        WHERE status = 'IN_FLIGHT';
END

-- changeset sdk:outbox-create-6
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_outbox_claimable')
BEGIN
    CREATE INDEX idx_outbox_claimable
        ON outbox_message(origin, seq)
        WHERE status IN ('PENDING', 'IN_FLIGHT');
END

-- changeset sdk:outbox-create-7
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_outbox_key_order')
BEGIN
    CREATE INDEX idx_outbox_key_order
        ON outbox_message(origin, topic, message_key, seq)
        WHERE status IN ('PENDING', 'IN_FLIGHT', 'FAILED');
END

-- changeset sdk:outbox-create-8
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_outbox_published')
BEGIN
    CREATE INDEX idx_outbox_published
        ON outbox_message(published_at)
        WHERE status = 'PUBLISHED';
END

-- changeset sdk:outbox-create-5
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'shedlock')
BEGIN
    CREATE TABLE shedlock (
        name NVARCHAR(64) NOT NULL,
        lock_until DATETIME2 NOT NULL,
        locked_at DATETIME2 NOT NULL,
        locked_by NVARCHAR(255) NOT NULL,
        PRIMARY KEY (name)
    );
END
