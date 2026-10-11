package io.codehunters.audit.config;

import io.codehunters.audit.AuditEventPublisherPort;
import io.codehunters.audit.outbox.OutboxAuditEventPublisher;
import io.codehunters.audit.outbox.OutboxAuditSettings;
import io.codehunters.audit.persistence.JsonSnapshotAuditEventPublisher;
import io.codehunters.messaging.OutboxWriter;
import io.codehunters.messaging.factory.OutboxEventFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot auto-configuration of the outbox audit sink, active with {@code
 * codehunters.audit.sink=outbox}. Registers {@code outboxAuditEventPublisher}, which writes every
 * audit event through the {@code OutboxWriter} of {@code sdk-event-messaging} in the caller's
 * transaction, to the topic {@code codehunters.audit.outbox.topic}; the JDBC sink and its executor
 * and mapper are then not registered.
 *
 * <p>Startup fails with an actionable message when the sink is selected but {@code
 * io.codehunters:sdk-event-messaging-jdbc} is not on the classpath, or when no {@code OutboxWriter}
 * and {@code OutboxEventFactory} beans exist.
 *
 * @since 5.1.0
 */
@AutoConfiguration(
    before = AuditAutoConfiguration.class,
    afterName = {
      "io.codehunters.messaging.jdbc.config.EventMessagingJdbcAutoConfiguration",
      "io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration"
    })
@ConditionalOnBooleanProperty(name = AuditAutoConfiguration.ENABLED, matchIfMissing = true)
@ConditionalOnProperty(name = "codehunters.audit.sink", havingValue = "outbox")
@EnableConfigurationProperties(AuditOutboxProperties.class)
public class AuditOutboxAutoConfiguration {

  private static final String MISSING_MESSAGING =
      "codehunters.audit.sink=outbox needs io.codehunters:sdk-event-messaging-jdbc on the"
          + " classpath; add it or remove the property to use the JDBC sink";

  private static final String MISSING_WRITER =
      "codehunters.audit.sink=outbox needs an OutboxWriter and an OutboxEventFactory bean; set"
          + " codehunters.outbox.enabled=true and configure sdk-event-messaging-jdbc";

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public AuditOutboxAutoConfiguration() {}

  /** Wires the publisher; every messaging type is confined here, loaded only when present. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "io.codehunters.messaging.OutboxWriter")
  static class OutboxSinkConfiguration {

    private final AuditOutboxProperties properties;
    private final ObjectProvider<JsonMapper> mapper;

    OutboxSinkConfiguration(
        AuditOutboxProperties properties,
        @Qualifier("outboxJsonMapper") ObjectProvider<JsonMapper> mapper) {
      this.properties = properties;
      this.mapper = mapper;
    }

    @Bean
    @ConditionalOnMissingBean(AuditEventPublisherPort.class)
    AuditEventPublisherPort outboxAuditEventPublisher(
        ObjectProvider<OutboxWriter> writer,
        ObjectProvider<OutboxEventFactory> factory,
        ObjectProvider<PlatformTransactionManager> txManager) {
      return create(
          new Collaborators(
              writer.getIfAvailable(), factory.getIfAvailable(), txManager.getIfUnique()),
          properties,
          mapper);
    }

    private static AuditEventPublisherPort create(
        Collaborators collaborators,
        AuditOutboxProperties properties,
        ObjectProvider<JsonMapper> mapper) {
      requirePresent(collaborators);
      var settings = new OutboxAuditSettings(properties.topic(), collaborators.txManager());
      return new JsonSnapshotAuditEventPublisher(
          new OutboxAuditEventPublisher(collaborators.writer(), collaborators.factory(), settings),
          mapper.getIfAvailable(() -> JsonMapper.builder().build()));
    }

    private static void requirePresent(Collaborators collaborators) {
      if (collaborators.writer() == null || collaborators.factory() == null) {
        throw new IllegalStateException(MISSING_WRITER);
      }
    }

    private record Collaborators(
        OutboxWriter writer, OutboxEventFactory factory, PlatformTransactionManager txManager) {}
  }

  /** Fails startup when the outbox sink is selected without the messaging SDK. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingClass("io.codehunters.messaging.OutboxWriter")
  static class MissingOutboxConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditEventPublisherPort.class)
    AuditEventPublisherPort outboxAuditEventPublisher() {
      throw new IllegalStateException(MISSING_MESSAGING);
    }
  }
}
