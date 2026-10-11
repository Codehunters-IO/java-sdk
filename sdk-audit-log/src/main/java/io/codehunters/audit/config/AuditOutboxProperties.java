package io.codehunters.audit.config;

/**
 * Configuration of the outbox audit sink.
 *
 * @param topic the outbox topic audit events are written to
 * @since 5.1.0
 */
public record AuditOutboxProperties(String topic) {}
