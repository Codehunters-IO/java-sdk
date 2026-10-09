package io.codehunters.messaging.tracing;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxMessage;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@link MessagePublisherPort} decorator that publishes each outbox message with the trace context
 * stored with it ({@link OutboxMessage#getTraceparent()}) as the current one, instead of the
 * dispatcher's. Logs written during the publish carry the request's trace id, and a span or
 * observation started inside it (for example a template observation) becomes a child of the stored
 * span. The dispatcher's context is restored afterwards, also when the publish fails.
 *
 * <p>A message without a stored context, or with one that is not a valid W3C {@code traceparent},
 * is published in the current context. Publishes without an outbox message are delegated as they
 * are.
 */
public final class TracedMessagePublisher implements MessagePublisherPort {

  private static final Pattern TRACEPARENT =
      Pattern.compile("[0-9a-f]{2}-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})");
  private static final Pattern ALL_ZEROS = Pattern.compile("0+");

  private final MessagePublisherPort delegate;
  private final Tracer tracer;

  /**
   * Creates the decorator.
   *
   * @param delegate the publisher that actually sends the messages
   * @param tracer the tracer whose current context is replaced during each publish
   */
  public TracedMessagePublisher(MessagePublisherPort delegate, Tracer tracer) {
    this.delegate = delegate;
    this.tracer = tracer;
  }

  @Override
  public void publish(String topic, String routingKey, String payload) {
    delegate.publish(topic, routingKey, payload);
  }

  @Override
  public void publish(String topic, String payload) {
    delegate.publish(topic, payload);
  }

  @Override
  public void publish(OutboxMessage<String> message) {
    Optional<TraceContext> stored = storedContext(message.getTraceparent());
    if (stored.isEmpty()) {
      delegate.publish(message);
      return;
    }
    try (CurrentTraceContext.Scope ignored = tracer.currentTraceContext().newScope(stored.get())) {
      delegate.publish(message);
    }
  }

  private Optional<TraceContext> storedContext(String traceparent) {
    var fields = traceparent == null ? null : TRACEPARENT.matcher(traceparent);
    if (fields == null || !fields.matches() || isAllZeros(fields.group(1), fields.group(2))) {
      return Optional.empty();
    }
    return Optional.of(
        tracer
            .traceContextBuilder()
            .traceId(fields.group(1))
            .spanId(fields.group(2))
            .sampled((Integer.parseInt(fields.group(3), 16) & 1) == 1)
            .build());
  }

  private static boolean isAllZeros(String traceId, String spanId) {
    return ALL_ZEROS.matcher(traceId).matches() || ALL_ZEROS.matcher(spanId).matches();
  }
}
