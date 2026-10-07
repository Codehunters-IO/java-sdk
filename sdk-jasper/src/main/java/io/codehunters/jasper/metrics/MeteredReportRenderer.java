package io.codehunters.jasper.metrics;

import io.codehunters.jasper.ReportRenderer;
import io.codehunters.jasper.ReportRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.OutputStream;
import java.util.function.Supplier;

/**
 * Times every render in the {@code report.render} timer, tagged with {@code format} ({@code pdf},
 * {@code xlsx}, {@code csv}) and {@code outcome} ({@code SUCCESS} or {@code ERROR}). Template names
 * are not tagged, to keep the meter's cardinality fixed. Failures are rethrown unchanged.
 */
public final class MeteredReportRenderer implements ReportRenderer {

  private final ReportRenderer delegate;
  private final MeterRegistry registry;

  /**
   * Wraps a renderer.
   *
   * @param delegate the renderer doing the work
   * @param registry where the timer is registered
   */
  public MeteredReportRenderer(ReportRenderer delegate, MeterRegistry registry) {
    if (delegate == null || registry == null) {
      throw new IllegalArgumentException("delegate and registry must not be null");
    }
    this.delegate = delegate;
    this.registry = registry;
  }

  @Override
  public void render(ReportRequest request, OutputStream out) {
    timed(
        request,
        () -> {
          delegate.render(request, out);
          return null;
        });
  }

  @Override
  public byte[] toBytes(ReportRequest request) {
    return timed(request, () -> delegate.toBytes(request));
  }

  private <T> T timed(ReportRequest request, Supplier<T> render) {
    if (request == null) {
      return render.get();
    }
    var sample = Timer.start(registry);
    var outcome = "ERROR";
    try {
      T result = render.get();
      outcome = "SUCCESS";
      return result;
    } finally {
      sample.stop(timer(request, outcome));
    }
  }

  private Timer timer(ReportRequest request, String outcome) {
    return Timer.builder("report.render")
        .description("Time to fill and export a report")
        .tag("format", request.format().extension())
        .tag("outcome", outcome)
        .register(registry);
  }
}
