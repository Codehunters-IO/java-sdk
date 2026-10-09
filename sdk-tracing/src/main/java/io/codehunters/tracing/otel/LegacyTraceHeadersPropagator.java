package io.codehunters.tracing.otel;

import io.codehunters.tracing.TraceHeaders;
import io.codehunters.tracing.TraceParent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.List;
import java.util.Optional;

/**
 * OpenTelemetry propagator for the trace headers of the gateway and of 0.x producers. It sits next
 * to the standard propagators Spring Boot configures and only fills the gap they leave:
 *
 * <ul>
 *   <li><b>Extract:</b> when the carrier has no valid {@code traceparent} and no earlier propagator
 *       extracted a remote parent, reads {@code x-traceparent} (W3C format), then {@code
 *       x-trace-id} with {@code x-span-id}, then {@code x-trace-id} alone (a sampled parent with a
 *       new span id). Ids that are not W3C ids are ignored.
 *   <li><b>Inject:</b> nothing by default, the standard {@code traceparent} is written by Boot's
 *       propagators; when enabled, also writes {@code x-traceparent} for consumers that read only
 *       that header.
 * </ul>
 *
 * <p>The result does not depend on its position in a composite propagator: a valid {@code
 * traceparent} always wins.
 */
public final class LegacyTraceHeadersPropagator implements TextMapPropagator {

  private static final List<String> FIELDS =
      List.of(TraceHeaders.LEGACY_TRACEPARENT, TraceHeaders.TRACE_ID, TraceHeaders.SPAN_ID);

  private final boolean inject;

  /**
   * Creates the propagator.
   *
   * @param inject whether to also write {@code x-traceparent} on outbound carriers
   */
  public LegacyTraceHeadersPropagator(boolean inject) {
    this.inject = inject;
  }

  @Override
  public List<String> fields() {
    return FIELDS;
  }

  @Override
  public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
    SpanContext span = Span.fromContext(context).getSpanContext();
    if (inject && span.isValid()) {
      String flags = span.getTraceFlags().asHex();
      TraceParent.of(span.getTraceId(), span.getSpanId(), flags)
          .ifPresent(
              parent -> setter.set(carrier, TraceHeaders.LEGACY_TRACEPARENT, parent.format()));
    }
  }

  @Override
  public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
    Context base = context == null ? Context.root() : context;
    if (carrier == null || hasRemoteParent(base) || hasTraceParent(carrier, getter)) {
      return base;
    }
    return legacyParent(carrier, getter)
        .map(parent -> base.with(Span.wrap(toSpanContext(parent))))
        .orElse(base);
  }

  private static boolean hasRemoteParent(Context context) {
    SpanContext span = Span.fromContext(context).getSpanContext();
    return span.isValid() && span.isRemote();
  }

  private static <C> boolean hasTraceParent(C carrier, TextMapGetter<C> getter) {
    return TraceParent.parse(getter.get(carrier, TraceHeaders.TRACEPARENT)).isPresent();
  }

  private static <C> Optional<TraceParent> legacyParent(C carrier, TextMapGetter<C> getter) {
    String traceId = getter.get(carrier, TraceHeaders.TRACE_ID);
    return TraceParent.parse(getter.get(carrier, TraceHeaders.LEGACY_TRACEPARENT))
        .or(() -> TraceParent.of(traceId, getter.get(carrier, TraceHeaders.SPAN_ID), null))
        .or(() -> TraceParent.ofTraceId(traceId));
  }

  private static SpanContext toSpanContext(TraceParent parent) {
    TraceFlags flags = parent.sampled() ? TraceFlags.getSampled() : TraceFlags.getDefault();
    return SpanContext.createFromRemoteParent(
        parent.traceId(), parent.spanId(), flags, TraceState.getDefault());
  }
}
