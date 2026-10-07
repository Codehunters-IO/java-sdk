package io.codehunters.messaging.infrastructure.drivenadapters.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.messaging.application.port.MessagePublishException;
import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration;
import io.codehunters.messaging.config.EventMessagingKafkaAutoConfiguration;
import io.codehunters.messaging.config.EventMessagingRabbitAutoConfiguration;
import io.codehunters.messaging.domain.model.OutboxMessage;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.rabbitmq.RabbitMQContainer;

class RabbitMessagePublisherAdapterIT {

  private static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-alpine");
  private static final String BOUND_EXCHANGE = "outbox.it.bound";
  private static final String UNBOUND_EXCHANGE = "outbox.it.unbound";
  private static final String MISSING_EXCHANGE = "outbox.it.missing";
  private static final String QUEUE = "outbox.it.queue";
  private static final String ROUTING_KEY = "outbox.created";
  private static final String PAYLOAD = "{\"orderId\":\"o-1\"}";
  private static final long RECEIVE_TIMEOUT_MS = 5_000L;

  private static CachingConnectionFactory consumerConnectionFactory;

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  EventMessagingRabbitAutoConfiguration.class,
                  EventMessagingCoreAutoConfiguration.class))
          .withClassLoader(new FilteredClassLoader(EventMessagingKafkaAutoConfiguration.class));

  @BeforeAll
  static void startBroker() {
    RABBIT.start();
    consumerConnectionFactory = connectionFactory(ConfirmType.NONE, false);
    declareTopology(consumerConnectionFactory);
  }

  @AfterAll
  static void stopBroker() {
    consumerConnectionFactory.destroy();
    RABBIT.stop();
  }

  @Test
  void should_deliver_message_with_message_id_and_id_header_when_routable_message_confirmed() {
    UUID id = UUID.randomUUID();

    withPublisher(publisher -> publisher.publish(message(BOUND_EXCHANGE, id)));

    Message received =
        new RabbitTemplate(consumerConnectionFactory).receive(QUEUE, RECEIVE_TIMEOUT_MS);
    assertThat(received.getMessageProperties().getMessageId()).isEqualTo(id.toString());
    assertThat(
            (String)
                received.getMessageProperties().getHeader(MessagePublisherPort.OUTBOX_ID_HEADER))
        .isEqualTo(id.toString());
    assertThat(new String(received.getBody(), StandardCharsets.UTF_8)).isEqualTo(PAYLOAD);
  }

  @Test
  void should_throw_publish_exception_when_exchange_has_no_binding_for_message() {
    withPublisher(
        publisher ->
            assertThatThrownBy(
                    () -> publisher.publish(message(UNBOUND_EXCHANGE, UUID.randomUUID())))
                .isInstanceOf(MessagePublishException.class)
                .hasMessage("Broker publish failed"));
  }

  @Test
  void should_throw_publish_exception_when_exchange_missing() {
    withPublisher(
        publisher ->
            assertThatThrownBy(
                    () -> publisher.publish(message(MISSING_EXCHANGE, UUID.randomUUID())))
                .isInstanceOf(MessagePublishException.class)
                .hasMessage("Broker publish failed"));
  }

  @Test
  void should_publish_again_when_previous_message_was_returned() {
    UUID id = UUID.randomUUID();

    withPublisher(
        publisher -> {
          assertThatThrownBy(() -> publisher.publish(message(UNBOUND_EXCHANGE, UUID.randomUUID())))
              .isInstanceOf(MessagePublishException.class);
          publisher.publish(message(BOUND_EXCHANGE, id));
        });

    Message received =
        new RabbitTemplate(consumerConnectionFactory).receive(QUEUE, RECEIVE_TIMEOUT_MS);
    assertThat(received.getMessageProperties().getMessageId()).isEqualTo(id.toString());
  }

  @Test
  void should_deliver_message_when_connection_factory_uses_correlated_confirms() {
    UUID id = UUID.randomUUID();

    withPublisher(
        ConfirmType.CORRELATED, publisher -> publisher.publish(message(BOUND_EXCHANGE, id)));

    Message received =
        new RabbitTemplate(consumerConnectionFactory).receive(QUEUE, RECEIVE_TIMEOUT_MS);
    assertThat(received.getMessageProperties().getMessageId()).isEqualTo(id.toString());
  }

  @Test
  void should_throw_publish_exception_when_correlated_confirms_and_no_binding_for_message() {
    withPublisher(
        ConfirmType.CORRELATED,
        publisher ->
            assertThatThrownBy(
                    () -> publisher.publish(message(UNBOUND_EXCHANGE, UUID.randomUUID())))
                .isInstanceOf(MessagePublishException.class));
  }

  @Test
  void should_fail_startup_when_connection_factory_lacks_confirms_and_returns() {
    contextRunner
        .withBean(ConnectionFactory.class, () -> connectionFactory(ConfirmType.NONE, false))
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("spring.rabbitmq.publisher-confirm-type=simple")
                    .hasMessageContaining("spring.rabbitmq.publisher-returns=true"));
  }

  private void withPublisher(Consumer<MessagePublisherPort> action) {
    withPublisher(ConfirmType.SIMPLE, action);
  }

  private void withPublisher(ConfirmType confirmType, Consumer<MessagePublisherPort> action) {
    contextRunner
        .withBean(ConnectionFactory.class, () -> connectionFactory(confirmType, true))
        .run(context -> action.accept(context.getBean(MessagePublisherPort.class)));
  }

  private static CachingConnectionFactory connectionFactory(
      ConfirmType confirmType, boolean returns) {
    CachingConnectionFactory connectionFactory =
        new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
    connectionFactory.setUsername(RABBIT.getAdminUsername());
    connectionFactory.setPassword(RABBIT.getAdminPassword());
    connectionFactory.setPublisherConfirmType(confirmType);
    connectionFactory.setPublisherReturns(returns);
    return connectionFactory;
  }

  private static void declareTopology(ConnectionFactory connectionFactory) {
    RabbitAdmin admin = new RabbitAdmin(connectionFactory);
    DirectExchange bound = new DirectExchange(BOUND_EXCHANGE);
    Queue queue = new Queue(QUEUE);
    admin.declareExchange(bound);
    admin.declareExchange(new DirectExchange(UNBOUND_EXCHANGE));
    admin.declareQueue(queue);
    Binding binding = BindingBuilder.bind(queue).to(bound).with(ROUTING_KEY);
    admin.declareBinding(binding);
  }

  private static OutboxMessage<String> message(String exchange, UUID id) {
    return OutboxMessage.<String>builder()
        .id(id)
        .topic(exchange)
        .routingKey(ROUTING_KEY)
        .key("o-1")
        .message(PAYLOAD)
        .build();
  }
}
