package io.codehunters.messaging.tracing;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.W3cTraceParent;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Optional;

/**
 * {@link MessagePublisherPort} decorator that publishes each outbox message with the trace context
 * stored with it (the {@link OutboxMessage} {@code traceparent}) as the current one, instead of the
 * dispatcher's. Logs written during the publish carry the request's trace id, and a span or
 * observation started inside it (for example a template observation) becomes a child of the stored
 * span. The dispatcher's context is restored afterwards, also when the publish fails.
 *
 * <p>A message without a stored context, or with one that is not a valid {@link W3cTraceParent}, is
 * published in the current context. Publishes without an outbox message are delegated as they are.
 */
public final class TracedMessagePublisher implements MessagePublisherPort {

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
    return W3cTraceParent.parse(traceparent).map(this::traceContextOf);
  }

  private TraceContext traceContextOf(W3cTraceParent stored) {
    return tracer
        .traceContextBuilder()
        .traceId(stored.traceId())
        .spanId(stored.spanId())
        .sampled(stored.sampled())
        .build();
  }
}
