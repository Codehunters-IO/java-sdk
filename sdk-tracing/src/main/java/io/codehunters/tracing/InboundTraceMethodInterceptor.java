package io.codehunters.tracing;

import java.util.Arrays;
import java.util.Optional;
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
        .filter(Message.class::isInstance)
        .map(argument -> ((Message) argument).getMessageProperties())
        .findFirst()
        .flatMap(InboundTraceMethodInterceptor::fromHeaders);
  }

  private static Optional<TraceParent> fromHeaders(MessageProperties properties) {
    return TraceParent.parse(header(properties, TraceHeaders.TRACEPARENT))
        .or(() -> TraceParent.parse(header(properties, TraceHeaders.LEGACY_TRACEPARENT)))
        .or(() -> TraceParent.ofTraceId(header(properties, TraceHeaders.TRACE_ID)));
  }

  private static String header(MessageProperties properties, String name) {
    Object value = properties.getHeader(name);
    return value == null ? null : value.toString();
  }
}
