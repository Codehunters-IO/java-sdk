package io.codehunters.messaging.rabbit.config;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.config.ConditionalOnOutboxTransport;
import io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration;
import io.codehunters.messaging.config.EventMessagingProperties;
import io.codehunters.messaging.rabbit.RabbitMessagePublisherAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the RabbitMQ outbox publisher. Loaded only when {@code
 * spring-rabbit} is on the classpath, {@code codehunters.outbox.enabled} is {@code true} (the
 * default) and RabbitMQ is the outbox transport: {@code codehunters.outbox.publisher.type} is
 * {@code rabbit}, or it is unset and {@code sdk-event-messaging-kafka} is absent (see {@link
 * ConditionalOnOutboxTransport}).
 *
 * <p>The publisher sends through its own {@code outboxRabbitTemplate}, built from the application's
 * {@link ConnectionFactory} and kept out of by-type injection, so the application's {@link
 * RabbitTemplate}, message converter and listeners are never touched. That factory MUST enable
 * publisher confirms and returns ({@code spring.rabbitmq.publisher-confirm-type=simple} or {@code
 * correlated}, {@code spring.rabbitmq.publisher-returns=true}); otherwise startup fails. Trace
 * propagation is left to {@code sdk-tracing}, which instruments every {@link RabbitTemplate} bean.
 *
 * <p>Runs before {@link EventMessagingCoreAutoConfiguration} so the publisher is visible to the use
 * case's {@code @ConditionalOnBean}.
 */
@Slf4j
@AutoConfiguration(
    before = EventMessagingCoreAutoConfiguration.class,
    afterName = "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration")
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(
    prefix = "codehunters.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnOutboxTransport("rabbit")
@ConditionalOnBean(ConnectionFactory.class)
public class EventMessagingRabbitAutoConfiguration {

  private static final String OUTBOX_TEMPLATE = "outboxRabbitTemplate";
  private static final String CONFIRMS_REQUIRED =
      "The outbox RabbitMQ publisher needs publisher confirms and returns on the connection"
          + " factory: set spring.rabbitmq.publisher-confirm-type=simple (or correlated) and"
          + " spring.rabbitmq.publisher-returns=true";

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public EventMessagingRabbitAutoConfiguration() {}

  /**
   * Provides the outbox's own {@link RabbitTemplate}: {@code mandatory=true}, so an unroutable
   * message comes back as {@code basic.return}, with a returns callback that logs it, publishing on
   * the factory's publisher connection when it has one (a {@code CachingConnectionFactory} does),
   * so a broker that blocks publishers never blocks the application's consumers. It is not a
   * default candidate, so it never replaces the application's template.
   *
   * @param connectionFactory the application's connection factory, with simple or correlated
   *     publisher confirms and returns enabled
   * @return the outbox template
   * @throws IllegalStateException when the connection factory lacks confirms or returns
   */
  @Bean(name = OUTBOX_TEMPLATE, defaultCandidate = false)
  @ConditionalOnMissingBean(name = OUTBOX_TEMPLATE)
  public RabbitTemplate outboxRabbitTemplate(ConnectionFactory connectionFactory) {
    if (!hasConfirms(connectionFactory) || !connectionFactory.isPublisherReturns()) {
      throw new IllegalStateException(CONFIRMS_REQUIRED);
    }
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMandatory(true);
    template.setUsePublisherConnection(true);
    template.setReturnsCallback(EventMessagingRabbitAutoConfiguration::logReturned);
    return template;
  }

  /**
   * Provides the RabbitMQ-backed message publisher.
   *
   * @param outboxRabbitTemplate the outbox template
   * @param properties the messaging configuration, whose RabbitMQ claim budget is validated
   * @return a {@link MessagePublisherPort}
   */
  @Bean
  @ConditionalOnMissingBean(MessagePublisherPort.class)
  public MessagePublisherPort messagePublisherPort(
      @Qualifier(OUTBOX_TEMPLATE) RabbitTemplate outboxRabbitTemplate,
      EventMessagingProperties properties) {
    properties.requireClaimBudget("rabbit");
    return new RabbitMessagePublisherAdapter(
        outboxRabbitTemplate, properties.publisher().rabbit().confirmTimeout());
  }

  // waitForConfirmsOrDie works on any channel in confirm mode, simple or correlated.
  private static boolean hasConfirms(ConnectionFactory connectionFactory) {
    return connectionFactory.isSimplePublisherConfirms() || connectionFactory.isPublisherConfirms();
  }

  private static void logReturned(ReturnedMessage returned) {
    log.warn(
        "Outbox message {} returned by exchange {} for routing key {} (reply code {})",
        returned.getMessage().getMessageProperties().getMessageId(),
        returned.getExchange(),
        returned.getRoutingKey(),
        returned.getReplyCode());
  }
}
