package io.codehunters.tracing;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stamps the trace context on published RabbitMQ messages: W3C {@code traceparent}, plus {@code
 * x-traceparent}, {@code x-trace-id} and {@code x-span-id} for consumers still on 0.x.
 *
 * <p>A valid {@code traceparent} already on the message (for example, one stored with an outbox
 * event) is kept. Otherwise the context comes from the MDC, then from the {@code header.traceId} of
 * an event envelope body, and is generated as a last resort.
 */
public class TracePropagationMessagePostProcessor implements MessagePostProcessor {

  private static final Logger log =
      LoggerFactory.getLogger(TracePropagationMessagePostProcessor.class);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** Creates the post-processor. */
  public TracePropagationMessagePostProcessor() {}

  @Override
  public Message postProcessMessage(Message message) throws AmqpException {
    MessageProperties properties = message.getMessageProperties();
    TraceParent context =
        existing(properties)
            .or(TraceMdc::current)
            .or(() -> fromEnvelope(message.getBody()))
            .orElseGet(TraceParent::generate);
    properties.setHeader(TraceHeaders.TRACEPARENT, context.format());
    properties.setHeader(TraceHeaders.LEGACY_TRACEPARENT, context.format());
    properties.setHeader(TraceHeaders.TRACE_ID, context.traceId());
    properties.setHeader(TraceHeaders.SPAN_ID, context.spanId());
    return message;
  }

  private static Optional<TraceParent> existing(MessageProperties properties) {
    Object header = properties.getHeader(TraceHeaders.TRACEPARENT);
    return header == null ? Optional.empty() : TraceParent.parse(header.toString());
  }

  private static Optional<TraceParent> fromEnvelope(byte[] body) {
    try {
      String traceId = JSON.readTree(body).path("header").path("traceId").asString(null);
      return TraceParent.ofTraceId(traceId);
    } catch (JacksonException e) {
      log.debug("Message body is not a JSON event envelope; generating a trace");
      return Optional.empty();
    }
  }
}
