package io.codehunters.messaging.infrastructure.drivenadapters.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RabbitMessagePublisherAdapterTest {

  @Mock private RabbitTemplate rabbitTemplate;
  @InjectMocks private RabbitMessagePublisherAdapter adapter;

  @Test
  void should_send_raw_bytes_with_json_content_type_when_routing_key_provided() {
    adapter.publish("payment.results", "payment.RAFFLE", "{\"status\":\"OK\"}");

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(rabbitTemplate).send(eq("payment.results"), eq("payment.RAFFLE"), captor.capture());
    Message message = captor.getValue();
    assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
        .isEqualTo("{\"status\":\"OK\"}");
    assertThat(message.getMessageProperties().getContentType())
        .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    assertThat(message.getMessageProperties().getContentEncoding()).isEqualTo("UTF-8");
    assertThat(message.getMessageProperties().getDeliveryMode())
        .isEqualTo(MessageDeliveryMode.PERSISTENT);
  }

  @Test
  void should_send_with_json_content_type_when_no_routing_key_provided() {
    adapter.publish("vitxo.notifications", "{\"status\":\"OK\"}");

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(rabbitTemplate).send(eq("vitxo.notifications"), eq(""), captor.capture());
    Message message = captor.getValue();
    assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
        .isEqualTo("{\"status\":\"OK\"}");
    assertThat(message.getMessageProperties().getContentType())
        .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    assertThat(message.getMessageProperties().getContentEncoding()).isEqualTo("UTF-8");
    assertThat(message.getMessageProperties().getDeliveryMode())
        .isEqualTo(MessageDeliveryMode.PERSISTENT);
  }

  @Test
  void should_pin_content_type_to_application_json_literal() {
    adapter.publish("topic", "rk", "{}");

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());
    assertThat(captor.getValue().getMessageProperties().getContentType())
        .isEqualTo("application/json");
  }

  @Test
  void should_set_persistent_delivery_mode_on_empty_routing_key_path() {
    adapter.publish("topic", "{}");

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(rabbitTemplate).send(anyString(), eq(""), captor.capture());
    assertThat(captor.getValue().getMessageProperties().getDeliveryMode())
        .isEqualTo(MessageDeliveryMode.PERSISTENT);
  }

  @Test
  void should_propagate_exception_on_publish_failure() {
    doThrow(new RuntimeException("connection lost"))
        .when(rabbitTemplate)
        .send(anyString(), anyString(), any(Message.class));

    assertThrows(RuntimeException.class, () -> adapter.publish("topic", "routing-key", "payload"));
  }
}
