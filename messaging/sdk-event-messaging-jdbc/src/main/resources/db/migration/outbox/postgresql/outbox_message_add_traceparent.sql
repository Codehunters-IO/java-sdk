--liquibase formatted sql

-- Upgrade script for PostgreSQL tables created from the 6.x to 8.x reference DDL (SDK 9.0.0).
-- Copy into your service changelog and adapt changeset author/id; never include it from the jar.
-- SDK 9.0.0 stores the W3C trace context of the work that created each row and sends it as the
-- traceparent broker header, so consumers continue the request's trace instead of the dispatcher's.
-- The insert and the claim reference traceparent, so saving and dispatch fail until this runs.
-- Apply it BEFORE deploying 9.0.0; 8.x ignores the column, so it is safe to add while 8.x is still
-- running. Adding a nullable column without a default does not rewrite the table. Rows written
-- before the upgrade keep traceparent NULL and are published without the header.

--changeset codehunters-sdk:outbox-message-add-traceparent
ALTER TABLE outbox_message ADD COLUMN IF NOT EXISTS traceparent VARCHAR(55);
--rollback ALTER TABLE outbox_message DROP COLUMN traceparent;
