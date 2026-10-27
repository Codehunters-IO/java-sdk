package io.codehunters.messaging.kafka.consumer;

import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.json.EnvelopeFormat;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the {@link EventMessage} envelope from a Kafka record whose value is the JSON string the
 * outbox publisher sent, together with the {@code outbox-message-id}, {@code traceparent} and
 * retry-attempt record headers.
 *
 * <p>The given mapper is rebuilt with the envelope pinned by {@link EnvelopeFormat} and unknown
 * fields ignored, so newer producers that add fields never make an envelope unreadable, whatever
 * the application's mapper sets. The reader never logs, and failures never carry the record value.
 */
public final class EnvelopeReader {

  /**
   * Spring Kafka's retry-topic attempt header ({@code RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS}).
   */
  static final String ATTEMPTS_HEADER = "retry_topic-attempts";

  private final JsonMapper mapper;

  /**
   * Creates the reader.
   *
   * @param mapper the mapper the payload is bound with; its settings are kept except the two above
   */
  public EnvelopeReader(JsonMapper mapper) {
    this.mapper =
        EnvelopeFormat.pin(
                mapper.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
            .build();
  }

  /**
   * Reads the envelope of a record.
   *
   * @param record the record, whose value is the envelope JSON
   * @param payloadType the class the payload is bound to
   * @param <T> the payload type
   * @return the envelope with its record headers
   * @throws UnreadableEnvelopeException when the value is not a complete, readable envelope
   */
  public <T> ReceivedEnvelope<T> read(ConsumerRecord<String, String> record, Class<T> payloadType) {
    EventMessage<T> message = parse(record.value(), payloadType);
    return new ReceivedEnvelope<>(
        message,
        header(record, MessagePublisherPort.OUTBOX_ID_HEADER),
        header(record, MessagePublisherPort.TRACEPARENT_HEADER),
        attempt(record));
  }

  private <T> EventMessage<T> parse(String value, Class<T> payloadType) {
    EventMessage<T> message = bind(value, payloadType);
    if (message == null || message.header() == null || message.body() == null) {
      throw new UnreadableEnvelopeException(UnreadableEnvelopeException.MISSING_HEADER_OR_BODY);
    }
    return message;
  }

  private <T> EventMessage<T> bind(String value, Class<T> payloadType) {
    if (value == null) {
      return null;
    }
    JavaType type =
        mapper.getTypeFactory().constructParametricType(EventMessage.class, payloadType);
    try {
      return mapper.readValue(value, type);
    } catch (JacksonException | IllegalArgumentException e) {
      throw new UnreadableEnvelopeException(e.getClass().getName());
    }
  }

  private static String header(ConsumerRecord<String, String> record, String name) {
    Header header = record.headers().lastHeader(name);
    return header == null || header.value() == null
        ? null
        : new String(header.value(), StandardCharsets.UTF_8);
  }

  // Spring Kafka writes the attempt as a 4-byte big-endian int; older versions wrote one byte.
  private static int attempt(ConsumerRecord<String, String> record) {
    Header header = record.headers().lastHeader(ATTEMPTS_HEADER);
    byte[] value = header == null ? null : header.value();
    int attempt = 1;
    if (value != null && value.length == 1) {
      attempt = Byte.toUnsignedInt(value[0]);
    } else if (value != null && value.length == Integer.BYTES) {
      attempt = ByteBuffer.wrap(value).getInt();
    }
    return Math.max(attempt, 1);
  }
}
