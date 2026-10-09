package io.codehunters.tracing.otel;

import io.codehunters.tracing.TraceHeaders;
import io.codehunters.tracing.TraceParent;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.List;
import java.util.Optional;

/**
 * OpenTelemetry propagator for the trace headers of the gateway and of 0.x producers. It sits next
 * to the standard propagators Spring Boot configures and only fills the gap they leave:
 *
 * <ul>
 *   <li><b>Extract:</b> when the carrier has no valid {@code traceparent} and no earlier propagator
 *       extracted a remote parent, reads {@code x-traceparent} (W3C format, its sampled flag kept),
 *       then {@code x-trace-id} with {@code x-span-id}, then {@code x-trace-id} alone (a parent
 *       with a new span id). Ids that are not W3C ids are ignored.
 *   <li><b>Sampling:</b> {@code x-trace-id} carries no flags, so the parent is marked sampled only
 *       when OpenTelemetry's trace-id ratio sampler, at the configured probability, samples its
 *       trace id: the decision Boot's root sampler would have made for that trace. Marking every
 *       such parent sampled would make Boot's parent-based sampler record all gateway traffic.
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

  private static final String SPAN_NAME = "legacy-trace-headers";

  private final boolean inject;
  private final Sampler sampler;

  /**
   * Creates the propagator.
   *
   * @param inject whether to also write {@code x-traceparent} on outbound carriers
   * @param samplingProbability the probability, from {@code 0.0} to {@code 1.0}, of sampling a
   *     parent read from {@code x-trace-id}, which carries no sampled flag; normally Boot's {@code
   *     management.tracing.sampling.probability}
   * @throws IllegalArgumentException when the probability is outside {@code [0.0, 1.0]}
   */
  public LegacyTraceHeadersPropagator(boolean inject, double samplingProbability) {
    if (!(samplingProbability >= 0.0 && samplingProbability <= 1.0)) {
      throw new IllegalArgumentException("Sampling probability must be between 0.0 and 1.0");
    }
    this.inject = inject;
    this.sampler = Sampler.traceIdRatioBased(samplingProbability);
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

  private <C> Optional<TraceParent> legacyParent(C carrier, TextMapGetter<C> getter) {
    return TraceParent.parse(getter.get(carrier, TraceHeaders.LEGACY_TRACEPARENT))
        .or(() -> legacyIds(carrier, getter).map(this::withSamplingDecision));
  }

  private static <C> Optional<TraceParent> legacyIds(C carrier, TextMapGetter<C> getter) {
    String traceId = getter.get(carrier, TraceHeaders.TRACE_ID);
    return TraceParent.of(traceId, getter.get(carrier, TraceHeaders.SPAN_ID), null)
        .or(() -> TraceParent.ofTraceId(traceId));
  }

  private TraceParent withSamplingDecision(TraceParent ids) {
    return new TraceParent(ids.traceId(), ids.spanId(), isSampled(ids.traceId()));
  }

  private boolean isSampled(String traceId) {
    SamplingResult result =
        sampler.shouldSample(
            Context.root(), traceId, SPAN_NAME, SpanKind.SERVER, Attributes.empty(), List.of());
    return result.getDecision() == SamplingDecision.RECORD_AND_SAMPLE;
  }

  private static SpanContext toSpanContext(TraceParent parent) {
    TraceFlags flags = parent.sampled() ? TraceFlags.getSampled() : TraceFlags.getDefault();
    return SpanContext.createFromRemoteParent(
        parent.traceId(), parent.spanId(), flags, TraceState.getDefault());
  }
}
