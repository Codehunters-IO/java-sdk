package io.codehunters.messaging.config;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.infrastructure.drivenadapters.rabbitmq.RabbitMessagePublisherAdapter;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the RabbitMQ outbox publisher. Loaded only when {@code
 * spring-rabbit} is on the classpath, {@code codehunters.outbox.enabled} is {@code true} (the default) and
 * {@code codehunters.outbox.publisher.type} is {@code rabbit} or unset.
 *
 * <p>Runs before {@link EventMessagingCoreAutoConfiguration} so the publisher is visible to the use
 * case's {@code @ConditionalOnBean}.
 */
@AutoConfiguration(
    before = EventMessagingCoreAutoConfiguration.class,
    afterName = "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration")
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(
    prefix = "codehunters.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnProperty(
    prefix = "codehunters.outbox.publisher",
    name = "type",
    havingValue = "rabbit",
    matchIfMissing = true)
public class EventMessagingRabbitAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public EventMessagingRabbitAutoConfiguration() {}

  /**
   * Provides the default Jackson message converter for RabbitMQ.
   *
   * @return a {@link JacksonJsonMessageConverter}
   */
  @Bean
  @ConditionalOnMissingBean(JacksonJsonMessageConverter.class)
  public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  /**
   * Provides the {@link RabbitTemplate}, attaching the trace-propagation post-processor when one is
   * available.
   *
   * <p><b>Note:</b> The {@link JacksonJsonMessageConverter} attached here is consumed only when
   * callers invoke {@link RabbitTemplate#convertAndSend} directly. The SDK's own {@link
   * RabbitMessagePublisherAdapter} uses {@link RabbitTemplate#send} with pre-built {@link
   * org.springframework.amqp.core.MessageProperties}, bypassing the converter. Do not remove the
   * converter: external SDK consumers may rely on it.
   *
   * @param connectionFactory the RabbitMQ connection factory
   * @param converter the message converter (used by external {@code convertAndSend} callers only)
   * @param tracePropagationMessagePostProcessor optional trace-propagation processor
   * @return a configured {@link RabbitTemplate}
   */
  @Bean
  @ConditionalOnBean(ConnectionFactory.class)
  @ConditionalOnMissingBean(RabbitTemplate.class)
  public RabbitTemplate rabbitTemplate(
      ConnectionFactory connectionFactory,
      JacksonJsonMessageConverter converter,
      ObjectProvider<MessagePostProcessor> tracePropagationMessagePostProcessor) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(converter);
    tracePropagationMessagePostProcessor.ifAvailable(
        rabbitTemplate::addBeforePublishPostProcessors);
    return rabbitTemplate;
  }

  /**
   * Provides the RabbitMQ-backed message publisher.
   *
   * @param rabbitTemplate the underlying {@link RabbitTemplate}
   * @return a {@link MessagePublisherPort}
   */
  @Bean
  @ConditionalOnMissingBean(MessagePublisherPort.class)
  @ConditionalOnBean(RabbitTemplate.class)
  public MessagePublisherPort messagePublisherPort(RabbitTemplate rabbitTemplate) {
    return new RabbitMessagePublisherAdapter(rabbitTemplate);
  }
}
