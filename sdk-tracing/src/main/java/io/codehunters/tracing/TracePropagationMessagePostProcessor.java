package io.codehunters.tracing;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbound RabbitMQ {@link MessagePostProcessor} that stamps trace/span headers (and a W3C {@code
 * traceparent}) onto published messages, deriving them from the {@link MDC}, the message body, or a
 * freshly generated value.
 */
public class TracePropagationMessagePostProcessor implements MessagePostProcessor {

  /** AMQP header carrying the W3C traceparent. */
  public static final String AMQP_HEADER_TRACEPARENT = "x-traceparent";

  /** AMQP header carrying the trace ID. */
  public static final String AMQP_HEADER_TRACE_ID = "x-trace-id";

  /** AMQP header carrying the span ID. */
  public static final String AMQP_HEADER_SPAN_ID = "x-span-id";

  private static final String MDC_TRACE_ID = "traceId";
  private static final String MDC_SPAN_ID = "spanId";
  private static final Logger log =
      LoggerFactory.getLogger(TracePropagationMessagePostProcessor.class);
  private static final int SPAN_ID_HEX_LENGTH = 16;
  private static final int TRACE_ID_HEX_LENGTH = 32;

  private final JsonMapper jsonMapper;
  private final SecureRandom random = new SecureRandom();

  /**
   * Creates the post-processor.
   *
   * @param jsonMapper the mapper used to read the trace ID from message bodies
   */
  public TracePropagationMessagePostProcessor(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Message postProcessMessage(Message message) throws AmqpException {
    String traceId = MDC.get(MDC_TRACE_ID);
    String spanId = MDC.get(MDC_SPAN_ID);

    if (traceId == null) {
      traceId = extractTraceIdFromBody(message);
    }
    if (traceId == null) {
      traceId = generateHex(TRACE_ID_HEX_LENGTH);
    }
    if (spanId == null) {
      spanId = generateHex(SPAN_ID_HEX_LENGTH);
    }

    message.getMessageProperties().setHeader(AMQP_HEADER_TRACE_ID, traceId);
    message.getMessageProperties().setHeader(AMQP_HEADER_SPAN_ID, spanId);
    message
        .getMessageProperties()
        .setHeader(AMQP_HEADER_TRACEPARENT, buildTraceparent(traceId, spanId));
    return message;
  }

  private String extractTraceIdFromBody(Message message) {
    try {
      JsonNode root = jsonMapper.readTree(message.getBody());
      JsonNode header = root.path("header");
      JsonNode traceId = header.path("traceId");
      return traceId.isMissingNode() || traceId.isNull() ? null : traceId.asString(null);
    } catch (RuntimeException e) {
      log.debug("Failed to extract traceId from message body: {}", e.getMessage());
      return null;
    }
  }

  private String buildTraceparent(String traceId, String spanId) {
    return "00-" + traceId + "-" + spanId + "-01";
  }

  private String generateHex(int hexLength) {
    byte[] bytes = new byte[hexLength / 2];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
