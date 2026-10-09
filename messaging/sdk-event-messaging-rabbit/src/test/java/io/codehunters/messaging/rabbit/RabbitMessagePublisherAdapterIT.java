package io.codehunters.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.codehunters.messaging.MessagePublishException;
import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxDispatchStore;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.SendOutboxMessagesPort;
import io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration;
import io.codehunters.messaging.kafka.config.EventMessagingKafkaAutoConfiguration;
import io.codehunters.messaging.rabbit.config.EventMessagingRabbitAutoConfiguration;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
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
  private static final String STORED_TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String STORED_TRACEPARENT = "00-" + STORED_TRACE + "-00f067aa0ba902b7-01";

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

  @Test
  void should_deliver_stored_traceparent_when_dispatch_runs_under_another_trace() {
    dispatchUnderAnotherTrace(contextRunner);

    Message received = receive();
    assertThat(traceIdOf(header(received, MessagePublisherPort.TRACEPARENT_HEADER)))
        .isEqualTo(STORED_TRACE);
    assertThat(header(received, MessagePublisherPort.TRACEPARENT_HEADER))
        .isEqualTo(STORED_TRACEPARENT);
  }

  @Test
  void should_deliver_no_legacy_traceparent_when_switch_is_off() {
    dispatchUnderAnotherTrace(contextRunner);

    assertThat(header(receive(), MessagePublisherPort.LEGACY_TRACEPARENT_HEADER)).isNull();
  }

  @Test
  void should_deliver_legacy_traceparent_when_switch_is_on() {
    dispatchUnderAnotherTrace(
        contextRunner.withPropertyValues(
            "codehunters.outbox.publisher.legacy-traceparent-header=true"));

    assertThat(header(receive(), MessagePublisherPort.LEGACY_TRACEPARENT_HEADER))
        .isEqualTo(STORED_TRACEPARENT);
  }

  // A scheduled dispatch: the scheduler's own span is current and its trace id is in the MDC.
  private static void dispatchUnderAnotherTrace(ApplicationContextRunner runner) {
    try (SdkTracerProvider provider = SdkTracerProvider.builder().build()) {
      Tracer tracer = otelTracer(provider);
      runner
          .withBean(ConnectionFactory.class, () -> connectionFactory(ConfirmType.SIMPLE, true))
          .withBean(OutboxDispatchStore.class, RabbitMessagePublisherAdapterIT::claimingTraced)
          .withBean(Tracer.class, () -> tracer)
          .run(
              context ->
                  dispatchInSchedulerSpan(tracer, context.getBean(SendOutboxMessagesPort.class)));
    }
  }

  private static void dispatchInSchedulerSpan(Tracer tracer, SendOutboxMessagesPort dispatch) {
    Span scheduler = tracer.nextSpan().name("scheduled-dispatch").start();
    try (Tracer.SpanInScope inScope = tracer.withSpan(scheduler);
        MDC.MDCCloseable mdc = MDC.putCloseable("traceId", scheduler.context().traceId())) {
      dispatch.execute();
    } finally {
      scheduler.end();
    }
  }

  private static Tracer otelTracer(SdkTracerProvider provider) {
    OtelCurrentTraceContext current = new OtelCurrentTraceContext();
    return new OtelTracer(
        provider.get("it"),
        current,
        event -> {},
        new OtelBaggageManager(current, List.of(), List.of()));
  }

  private static OutboxDispatchStore claimingTraced() {
    OutboxMessage<String> stored =
        message(BOUND_EXCHANGE, UUID.randomUUID()).toBuilder()
            .traceparent(STORED_TRACEPARENT)
            .build();
    OutboxDispatchStore repository = mock(OutboxDispatchStore.class);
    when(repository.claim(any())).thenReturn(List.of(stored));
    when(repository.markSendStarted(any())).thenReturn(Optional.of(stored));
    return repository;
  }

  private static Message receive() {
    return new RabbitTemplate(consumerConnectionFactory).receive(QUEUE, RECEIVE_TIMEOUT_MS);
  }

  private static String header(Message message, String name) {
    return message.getMessageProperties().getHeader(name);
  }

  private static String traceIdOf(String traceparent) {
    return traceparent.split("-")[1];
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
