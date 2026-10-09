package io.codehunters.messaging.config;

import io.codehunters.messaging.MessagePublisherDecorator;
import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxRepositoryPort;
import io.codehunters.messaging.SendOutboxMessagesPort;
import io.codehunters.messaging.dispatch.SendOutboxMessagesUseCase;
import io.codehunters.messaging.factory.EventMessageFactory;
import io.codehunters.messaging.factory.OutboxEventFactory;
import io.codehunters.messaging.factory.TraceIdSource;
import io.codehunters.messaging.metrics.MeteredMessagePublisher;
import io.codehunters.tracing.TraceContextPort;
import io.codehunters.tracing.TraceParent;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for the transport-neutral part of the transactional-outbox SDK.
 * Binds {@link EventMessagingProperties} and wires the UTC clock, the message factories and the
 * dispatch use case. Active when {@code codehunters.outbox.enabled} is {@code true} (the default).
 *
 * <p>The use case needs an {@link OutboxRepositoryPort} and a {@link MessagePublisherPort}, which
 * come from {@code sdk-event-messaging-jdbc} and a transport artifact ({@code
 * sdk-event-messaging-rabbit} or {@code sdk-event-messaging-kafka}). This configuration therefore
 * runs after their auto-configurations, referenced by name because any of them may be absent. It
 * also runs after Boot's metrics auto-configuration so a {@link MeterRegistry} bean, when present,
 * turns on the {@code outbox.publish} timer, and after {@code sdk-tracing}'s so its {@link
 * TraceContextPort}, when present, supplies the trace id of events created without one.
 */
@AutoConfiguration(
    afterName = {
      TransportAutoConfigurations.JDBC,
      TransportAutoConfigurations.RABBIT,
      TransportAutoConfigurations.KAFKA,
      "io.codehunters.tracing.config.TracingAutoConfiguration",
      "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
      "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
      "org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration"
    })
@ConditionalOnProperty(
    prefix = "codehunters.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(EventMessagingProperties.class)
public class EventMessagingCoreAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public EventMessagingCoreAutoConfiguration() {}

  /**
   * Provides the UTC clock used for outbox timestamps, backoff and purge cut-offs. An
   * application-defined {@link Clock} bean takes precedence.
   *
   * @return the system UTC clock
   */
  @Bean
  @ConditionalOnMissingBean(Clock.class)
  public Clock outboxClock() {
    return Clock.systemUTC();
  }

  /**
   * Provides the event-message factory bound to the current application name. Events created
   * without a trace id get the current one when a {@link TraceIdSource} bean exists.
   *
   * @param origin the originating application name
   * @param traceIdSource the current-trace source, if any
   * @return an {@link EventMessageFactory}
   */
  @Bean
  @ConditionalOnMissingBean(EventMessageFactory.class)
  public EventMessageFactory eventMessageFactory(
      @Value("${spring.application.name:unknown}") String origin,
      ObjectProvider<TraceIdSource> traceIdSource) {
    return new EventMessageFactory(origin, traceIdSource.getIfAvailable(TraceIdSource::none));
  }

  /**
   * Provides the outbox-message factory.
   *
   * @param eventMessageFactory the underlying event-message factory
   * @param origin the originating application name
   * @return an {@link OutboxEventFactory}
   */
  @Bean
  @ConditionalOnMissingBean(OutboxEventFactory.class)
  public OutboxEventFactory outboxEventFactory(
      EventMessageFactory eventMessageFactory,
      @Value("${spring.application.name:unknown}") String origin) {
    return new OutboxEventFactory(origin, eventMessageFactory);
  }

  /**
   * Provides the use case that claims and publishes pending outbox messages.
   *
   * @param outboxRepositoryPort the outbox repository
   * @param messagePublisherPort the message publisher
   * @param decorators the decorators applied to the publisher, in bean order
   * @param properties the messaging configuration
   * @param applicationName the originating application name (used as instance prefix)
   * @param clock the outbox clock
   * @return a {@link SendOutboxMessagesPort}
   */
  @Bean
  @ConditionalOnMissingBean(SendOutboxMessagesPort.class)
  @ConditionalOnBean({OutboxRepositoryPort.class, MessagePublisherPort.class})
  public SendOutboxMessagesPort sendOutboxMessagesPort(
      OutboxRepositoryPort outboxRepositoryPort,
      MessagePublisherPort messagePublisherPort,
      ObjectProvider<MessagePublisherDecorator> decorators,
      EventMessagingProperties properties,
      @Value("${spring.application.name:unknown}") String applicationName,
      Clock clock) {
    return new SendOutboxMessagesUseCase(
        outboxRepositoryPort,
        decorate(messagePublisherPort, decorators),
        properties.batchSize(),
        properties.maxAttempts(),
        resolveInstanceId(applicationName),
        applicationName,
        properties.claim().ttlMs(),
        properties.backoff().baseDelayMs(),
        properties.backoff().maxDelayMs(),
        clock);
  }

  private static MessagePublisherPort decorate(
      MessagePublisherPort publisher, ObjectProvider<MessagePublisherDecorator> decorators) {
    MessagePublisherPort decorated = publisher;
    for (MessagePublisherDecorator decorator : decorators.orderedStream().toList()) {
      decorated = decorator.decorate(decorated);
    }
    return decorated;
  }

  private String resolveInstanceId(String applicationName) {
    String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
    return applicationName + "@" + runtimeName;
  }

  /** Publish-timer wiring. Loaded only when Micrometer is on the classpath. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(MeterRegistry.class)
  static class OutboxPublisherMetricsConfiguration {

    /**
     * Wraps the dispatch publisher in a {@link MeteredMessagePublisher}.
     *
     * @param meterRegistry the application's meter registry
     * @return the timing decorator
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(name = "outboxPublisherMetricsDecorator")
    MessagePublisherDecorator outboxPublisherMetricsDecorator(MeterRegistry meterRegistry) {
      return publisher -> new MeteredMessagePublisher(publisher, meterRegistry);
    }
  }

  /** Current-trace wiring. Loaded only when {@code sdk-tracing} is on the classpath. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(TraceContextPort.class)
  static class OutboxTracingConfiguration {

    /**
     * Reads the current trace id from {@code sdk-tracing}'s {@link TraceContextPort}.
     *
     * @param traceContextPort the trace context of the current thread
     * @return a {@link TraceIdSource}
     */
    @Bean
    @ConditionalOnBean(TraceContextPort.class)
    @ConditionalOnMissingBean(TraceIdSource.class)
    TraceIdSource outboxTraceIdSource(TraceContextPort traceContextPort) {
      return () -> traceContextPort.currentTraceParent().map(TraceParent::traceId);
    }
  }
}
