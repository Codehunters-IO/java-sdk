package io.codehunters.messaging.infrastructure.drivenadapters.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ReturnListener;
import io.codehunters.messaging.application.port.MessagePublishException;
import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.domain.model.OutboxMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitOperations.OperationsCallback;

class RabbitMessagePublisherAdapterTest {

  private static final String EXCHANGE = "payment.results";
  private static final String ROUTING_KEY = "payment.RAFFLE";
  private static final String PAYLOAD = "{\"status\":\"OK\"}";
  private static final String PII_SENTINEL = "PII-SENTINEL-4242 holder Juan";
  private static final UUID ID = UUID.fromString("0b9c6a3e-7d1f-4a51-9f0e-2a7c1d4e5f60");
  private static final Duration CONFIRM_TIMEOUT = Duration.ofMillis(1500);
  private static final int NO_ROUTE = 312;

  private final RabbitOperations template = mock(RabbitOperations.class);
  private final RabbitOperations dedicated = mock(RabbitOperations.class);
  private final Channel channel = mock(Channel.class);
  private final RabbitMessagePublisherAdapter adapter =
      new RabbitMessagePublisherAdapter(template, CONFIRM_TIMEOUT);

  private ReturnListener returnListener;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() throws Exception {
    when(template.invoke(any()))
        .thenAnswer(call -> call.<OperationsCallback<?>>getArgument(0).doInRabbit(dedicated));
    when(dedicated.execute(any()))
        .thenAnswer(call -> call.<ChannelCallback<?>>getArgument(0).doInRabbit(channel));
    doAnswer(call -> returnListener = call.getArgument(0))
        .when(channel)
        .addReturnListener(any(ReturnListener.class));
  }

  @Test
  void should_send_outbox_message_with_message_id_and_id_header_when_publishing_message() {
    adapter.publish(message(ROUTING_KEY));

    Message sent = sentMessage(EXCHANGE, ROUTING_KEY);
    assertThat(sent.getMessageProperties().getMessageId()).isEqualTo(ID.toString());
    assertThat(
            (String) sent.getMessageProperties().getHeader(MessagePublisherPort.OUTBOX_ID_HEADER))
        .isEqualTo(ID.toString());
    assertThat(new String(sent.getBody(), StandardCharsets.UTF_8)).isEqualTo(PAYLOAD);
  }

  @Test
  void should_send_json_persistent_message_when_publishing_message() {
    adapter.publish(message(ROUTING_KEY));

    MessageProperties properties = sentMessage(EXCHANGE, ROUTING_KEY).getMessageProperties();
    assertThat(properties.getContentType()).isEqualTo("application/json");
    assertThat(properties.getContentEncoding()).isEqualTo("UTF-8");
    assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
  }

  @Test
  void should_use_empty_routing_key_when_outbox_message_has_none() {
    adapter.publish(message(null));

    assertThat(sentMessage(EXCHANGE, "").getMessageProperties().getMessageId())
        .isEqualTo(ID.toString());
  }

  @Test
  void should_wait_for_confirm_within_timeout_when_publishing() {
    adapter.publish(message(ROUTING_KEY));

    verify(dedicated).waitForConfirmsOrDie(CONFIRM_TIMEOUT.toMillis());
  }

  @Test
  void should_send_json_message_without_id_when_legacy_routing_key_publish_used() {
    adapter.publish(EXCHANGE, ROUTING_KEY, PAYLOAD);

    Message sent = sentMessage(EXCHANGE, ROUTING_KEY);
    assertThat(sent.getMessageProperties().getMessageId()).isNull();
    assertThat(sent.getMessageProperties().getContentType()).isEqualTo("application/json");
  }

  @Test
  void should_send_with_empty_routing_key_when_legacy_topic_publish_used() {
    adapter.publish(EXCHANGE, PAYLOAD);

    assertThat(sentMessage(EXCHANGE, "").getMessageProperties().getDeliveryMode())
        .isEqualTo(MessageDeliveryMode.PERSISTENT);
  }

  @Test
  void should_throw_publish_exception_without_broker_text_when_broker_nacks() {
    doThrow(new AmqpException(PII_SENTINEL)).when(dedicated).waitForConfirmsOrDie(anyLong());

    assertThatThrownBy(() -> adapter.publish(message(ROUTING_KEY)))
        .isInstanceOf(MessagePublishException.class)
        .hasMessage("Broker publish failed")
        .hasMessageNotContaining("PII-SENTINEL");
  }

  @Test
  void should_throw_publish_exception_caused_by_timeout_when_confirm_never_arrives() {
    doThrow(new AmqpTimeoutException("timed out")).when(dedicated).waitForConfirmsOrDie(anyLong());

    assertThatThrownBy(() -> adapter.publish(message(ROUTING_KEY)))
        .isInstanceOf(MessagePublishException.class)
        .hasCauseInstanceOf(AmqpTimeoutException.class);
  }

  @Test
  void should_throw_publish_exception_without_broker_text_when_broker_returns_message() {
    doAnswer(call -> returnUnroutable())
        .when(dedicated)
        .send(anyString(), anyString(), any(Message.class));

    assertThatThrownBy(() -> adapter.publish(message(ROUTING_KEY)))
        .isInstanceOf(MessagePublishException.class)
        .hasMessage("Broker publish failed")
        .hasMessageNotContaining("PII-SENTINEL");
  }

  @Test
  void should_remove_return_listener_when_publish_completes() {
    adapter.publish(message(ROUTING_KEY));

    verify(channel).removeReturnListener(returnListener);
  }

  private Object returnUnroutable() throws Exception {
    AMQP.BasicProperties properties =
        new AMQP.BasicProperties.Builder().messageId(ID.toString()).build();
    returnListener.handleReturn(
        NO_ROUTE, PII_SENTINEL, EXCHANGE, ROUTING_KEY, properties, new byte[0]);
    return null;
  }

  private Message sentMessage(String exchange, String routingKey) {
    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(dedicated).send(eq(exchange), eq(routingKey), captor.capture());
    return captor.getValue();
  }

  private static OutboxMessage<String> message(String routingKey) {
    return OutboxMessage.<String>builder()
        .id(ID)
        .topic(EXCHANGE)
        .routingKey(routingKey)
        .key("k-1")
        .message(PAYLOAD)
        .build();
  }
}
