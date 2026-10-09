-- Upgrade script for existing SQL Server installs (SDK 7.0.0 breaking change).
-- Copy into your service changelog and adapt changeset author/id; never include it from the jar.
-- SDK 7.0.0 marks each claimed row right before publishing it and, on reclaim after a crash, charges
-- an attempt only to marked rows. The claim and the mark reference send_started_at, so dispatch fails
-- until this runs. Apply it BEFORE deploying 7.0.0; 6.x ignores the column, so it is safe to add
-- while 6.x is still running. Tables older than 6.0.0 need sqlserver/outbox_message_add_seq.sql first.

-- changeset sdk:outbox-add-send-started-at-1
IF COL_LENGTH('outbox_message', 'send_started_at') IS NULL
BEGIN
    ALTER TABLE outbox_message ADD send_started_at DATETIME2 NULL;
END
