/**
 * The outbox audit sink: writes each audit event through the transactional outbox of {@code
 * sdk-event-messaging}, in the caller's transaction, so the audit trail records what commits.
 * Selected with {@code codehunters.audit.sink=outbox}.
 */
package io.codehunters.audit.outbox;
