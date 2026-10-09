package io.codehunters.tracing.amqp;

import io.codehunters.tracing.TraceHeaders;
import io.codehunters.tracing.TraceParent;
import io.codehunters.tracing.internal.TraceMdc;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/**
 * Continues the producer's trace in RabbitMQ listeners: reads {@code traceparent}, then {@code
 * x-traceparent}, then {@code x-trace-id}, and puts a child span in the MDC for the duration of the
 * listener call. Leaves an already active context alone.
 */
public class InboundTraceMethodInterceptor implements MethodInterceptor {

  /** Creates the interceptor. */
  public InboundTraceMethodInterceptor() {}

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    Optional<TraceParent> incoming =
        TraceMdc.isActive() ? Optional.empty() : incoming(invocation.getArguments());
    if (incoming.isEmpty()) {
      return invocation.proceed();
    }
    TraceMdc.put(incoming.get().child());
    try {
      return invocation.proceed();
    } finally {
      TraceMdc.clear();
    }
  }

  private static Optional<TraceParent> incoming(Object[] arguments) {
    return Arrays.stream(arguments == null ? new Object[0] : arguments)
        .flatMap(InboundTraceMethodInterceptor::messages)
        .findFirst()
        .flatMap(message -> fromHeaders(message.getMessageProperties()));
  }

  /** A single message, or the messages of a batch listener. */
  private static Stream<Message> messages(Object argument) {
    if (argument instanceof Message message) {
      return Stream.of(message);
    }
    if (argument instanceof List<?> batch) {
      return batch.stream().filter(Message.class::isInstance).map(Message.class::cast);
    }
    return Stream.empty();
  }

  private static Optional<TraceParent> fromHeaders(MessageProperties properties) {
    return TraceParent.parse(header(properties, TraceHeaders.TRACEPARENT))
        .or(() -> TraceParent.parse(header(properties, TraceHeaders.LEGACY_TRACEPARENT)))
        .or(() -> TraceParent.ofTraceId(header(properties, TraceHeaders.TRACE_ID)));
  }

  private static String header(MessageProperties properties, String name) {
    return AmqpHeaders.get(properties, name);
  }
}
