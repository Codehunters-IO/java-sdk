package io.codehunters.tracing.mdc;

import io.codehunters.tracing.TraceContextPort;
import io.codehunters.tracing.TraceHeaders;
import io.codehunters.tracing.TraceParent;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * {@link TraceContextPort} backed by the logging MDC. The fallback used when the application has no
 * Micrometer {@code Tracer}: it reports whatever other code put under the {@code traceId} / {@code
 * spanId} keys, and never starts or propagates a trace itself. This SDK no longer writes those
 * keys: without a tracer bridge they are empty unless the application fills them.
 */
public class MdcTraceContextAdapter implements TraceContextPort {

  private static final Pattern FLAGS = Pattern.compile("[0-9a-f]{2}");
  private static final String NOT_SAMPLED = "00";

  /** Creates the adapter. */
  public MdcTraceContextAdapter() {}

  @Override
  public Optional<String> currentTraceId() {
    return Optional.ofNullable(MDC.get(TraceHeaders.MDC_TRACE_ID));
  }

  @Override
  public Optional<String> currentSpanId() {
    return Optional.ofNullable(MDC.get(TraceHeaders.MDC_SPAN_ID));
  }

  /**
   * {@inheritDoc}
   *
   * <p>The sampled flag is read from the {@code traceFlags} MDC key. When that key is absent or not
   * two hex characters, the context is reported as <b>not sampled</b>: nothing here made a sampling
   * decision, and claiming one would make every parent-based sampler downstream record the trace.
   * When the MDC holds only a W3C trace id, the trace is kept with a new span id rather than lost.
   */
  @Override
  public Optional<TraceParent> currentTraceParent() {
    String traceId = MDC.get(TraceHeaders.MDC_TRACE_ID);
    String flags = knownFlags(MDC.get(TraceHeaders.MDC_TRACE_FLAGS));
    return TraceParent.of(traceId, MDC.get(TraceHeaders.MDC_SPAN_ID), flags)
        .or(
            () ->
                TraceParent.ofTraceId(traceId)
                    .flatMap(ids -> TraceParent.of(ids.traceId(), ids.spanId(), flags)));
  }

  private static String knownFlags(String flags) {
    return flags != null && FLAGS.matcher(flags).matches() ? flags : NOT_SAMPLED;
  }
}
