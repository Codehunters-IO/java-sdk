package io.codehunters.messaging.config;

/**
 * Class names of the transport auto-configurations, which core can only refer to as strings because
 * any of the transport artifacts may be absent. Each transport module tests that its name matches.
 *
 * <p>Public only so those tests can read it; not API, and it may change in any release.
 */
public final class TransportAutoConfigurations {

  /** {@code sdk-event-messaging-jdbc}'s auto-configuration. */
  public static final String JDBC =
      "io.codehunters.messaging.jdbc.config.EventMessagingJdbcAutoConfiguration";

  /** {@code sdk-event-messaging-rabbit}'s auto-configuration. */
  public static final String RABBIT =
      "io.codehunters.messaging.rabbit.config.EventMessagingRabbitAutoConfiguration";

  /** {@code sdk-event-messaging-kafka}'s auto-configuration. */
  public static final String KAFKA =
      "io.codehunters.messaging.kafka.config.EventMessagingKafkaAutoConfiguration";

  private TransportAutoConfigurations() {}
}
