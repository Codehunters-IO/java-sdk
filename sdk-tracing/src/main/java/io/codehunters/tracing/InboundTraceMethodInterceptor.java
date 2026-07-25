package io.codehunters.tracing;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;

/**
 * AOP interceptor for inbound RabbitMQ listeners that copies trace/span headers from the incoming
 * {@link Message} into the {@link MDC} for the duration of the listener invocation.
 */
public class InboundTraceMethodInterceptor implements MethodInterceptor {

  /** AMQP header carrying the trace ID. */
  public static final String AMQP_HEADER_TRACE_ID = "x-trace-id";

  /** AMQP header carrying the span ID. */
  public static final String AMQP_HEADER_SPAN_ID = "x-span-id";

  /** MDC key holding the trace ID. */
  public static final String MDC_TRACE_ID = "traceId";

  /** MDC key holding the span ID. */
  public static final String MDC_SPAN_ID = "spanId";

  /** Creates the interceptor. */
  public InboundTraceMethodInterceptor() {}

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    Message message = findMessage(invocation.getArguments());
    String traceId = readHeader(message, AMQP_HEADER_TRACE_ID);
    String spanId = readHeader(message, AMQP_HEADER_SPAN_ID);

    boolean putTrace = pushMdc(MDC_TRACE_ID, traceId);
    boolean putSpan = pushMdc(MDC_SPAN_ID, spanId);
    try {
      return invocation.proceed();
    } finally {
      if (putTrace) {
        MDC.remove(MDC_TRACE_ID);
      }
      if (putSpan) {
        MDC.remove(MDC_SPAN_ID);
      }
    }
  }

  private Message findMessage(Object[] args) {
    if (args == null) {
      return null;
    }
    for (Object arg : args) {
      if (arg instanceof Message message) {
        return message;
      }
    }
    return null;
  }

  private String readHeader(Message message, String key) {
    if (message == null) {
      return null;
    }
    Object value = message.getMessageProperties().getHeader(key);
    return value == null ? null : value.toString();
  }

  private boolean pushMdc(String key, String value) {
    if (value == null || MDC.get(key) != null) {
      return false;
    }
    MDC.put(key, value);
    return true;
  }
}
