--liquibase formatted sql

-- Upgrade script for PostgreSQL tables created from the 6.x reference DDL (SDK 7.0.0 breaking change).
-- Copy into your service changelog and adapt changeset author/id; never include it from the jar.
-- SDK 7.0.0 marks each claimed row right before publishing it and, on reclaim after a crash, charges
-- an attempt only to marked rows. The claim and the mark reference send_started_at, so dispatch fails
-- until this runs. Apply it BEFORE deploying 7.0.0; 6.x ignores the column, so it is safe to add
-- while 6.x is still running. Adding a nullable column without a default does not rewrite the table.

--changeset codehunters-sdk:outbox-message-add-send-started-at
ALTER TABLE outbox_message ADD COLUMN IF NOT EXISTS send_started_at TIMESTAMP;
--rollback ALTER TABLE outbox_message DROP COLUMN send_started_at;
