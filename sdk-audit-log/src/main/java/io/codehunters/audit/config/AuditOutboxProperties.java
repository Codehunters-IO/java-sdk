package io.codehunters.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the outbox audit sink, bound to {@code codehunters.audit.outbox.*} and read only
 * when {@code codehunters.audit.sink=outbox}.
 *
 * @param topic the outbox topic every audit event is written to; required and never blank
 * @since 5.1.0
 */
@ConfigurationProperties("codehunters.audit.outbox")
public record AuditOutboxProperties(String topic) {

  /**
   * Validates the topic.
   *
   * @throws IllegalArgumentException when the topic is null or blank
   */
  public AuditOutboxProperties {
    if (topic == null || topic.isBlank()) {
      throw new IllegalArgumentException("codehunters.audit.outbox.topic must not be blank");
    }
  }
}
