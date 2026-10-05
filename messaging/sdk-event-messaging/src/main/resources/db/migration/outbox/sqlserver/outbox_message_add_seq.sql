-- Upgrade script for existing SQL Server installs (SDK 6.0.0 breaking change).
-- Copy into your service changelog and adapt changeset author/id; never include it from the jar.
-- SDK 6.0.0 claims and orders outbox rows by seq instead of occurred_at, and blocks a row while an
-- earlier row with the same (origin, topic, message_key) is PENDING, IN_FLIGHT or FAILED. Tables
-- created from the pre-6.0.0 reference DDL have no seq column, so the claim fails until this runs.
-- Existing rows receive seq values in physical order; drain the outbox before upgrading if the
-- relative order of rows already queued matters.

-- changeset sdk:outbox-add-seq-1
IF COL_LENGTH('outbox_message', 'seq') IS NULL
BEGIN
    ALTER TABLE outbox_message ADD seq BIGINT IDENTITY(1,1) NOT NULL;
END

-- changeset sdk:outbox-add-seq-2
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_outbox_claimable')
BEGIN
    CREATE INDEX idx_outbox_claimable
        ON outbox_message(origin, seq)
        WHERE status IN ('PENDING', 'IN_FLIGHT');
END

-- changeset sdk:outbox-add-seq-3
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_outbox_key_order')
BEGIN
    CREATE INDEX idx_outbox_key_order
        ON outbox_message(origin, topic, message_key, seq)
        WHERE status IN ('PENDING', 'IN_FLIGHT', 'FAILED');
END

-- changeset sdk:outbox-add-seq-4
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_outbox_published')
BEGIN
    CREATE INDEX idx_outbox_published
        ON outbox_message(published_at)
        WHERE status = 'PUBLISHED';
END
