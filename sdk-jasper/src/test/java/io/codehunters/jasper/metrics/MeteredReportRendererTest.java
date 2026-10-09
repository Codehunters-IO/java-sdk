package io.codehunters.jasper.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.jasper.ReportException;
import io.codehunters.jasper.ReportFormat;
import io.codehunters.jasper.ReportRenderer;
import io.codehunters.jasper.ReportRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import org.junit.jupiter.api.Test;

class MeteredReportRendererTest {

  private static final ReportRequest REQUEST = ReportRequest.of("orders.jrxml", ReportFormat.PDF);
  private static final byte[] DOCUMENT = {1, 2, 3};

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void should_time_a_successful_render_by_format() {
    var renderer = new MeteredReportRenderer(succeeding(), registry);

    renderer.render(REQUEST, new ByteArrayOutputStream());

    assertThat(count("pdf", "SUCCESS")).isEqualTo(1);
  }

  @Test
  void should_time_to_bytes_and_return_the_document() {
    var renderer = new MeteredReportRenderer(succeeding(), registry);

    assertThat(renderer.toBytes(REQUEST.withData(io.codehunters.jasper.ReportData.empty())))
        .isEqualTo(DOCUMENT);
    assertThat(count("pdf", "SUCCESS")).isEqualTo(1);
  }

  @Test
  void should_record_a_failure_and_rethrow_it_unchanged() {
    var failure = new ReportException("report orders.jrxml could not be filled");
    var renderer = new MeteredReportRenderer(failing(failure), registry);

    assertThatThrownBy(() -> renderer.toBytes(REQUEST)).isSameAs(failure);
    assertThat(count("pdf", "ERROR")).isEqualTo(1);
  }

  @Test
  void should_pass_a_missing_request_to_the_delegate_without_recording() {
    var renderer =
        new MeteredReportRenderer(failing(new IllegalArgumentException("request")), registry);

    assertThatIllegalArgumentException().isThrownBy(() -> renderer.toBytes(null));
    assertThat(registry.getMeters()).isEmpty();
  }

  @Test
  void should_close_the_renderer_it_wraps() {
    var closed = new java.util.concurrent.atomic.AtomicBoolean();
    var delegate =
        new ReportRenderer() {
          @Override
          public void render(ReportRequest request, OutputStream out) {}

          @Override
          public byte[] toBytes(ReportRequest request) {
            return DOCUMENT;
          }

          @Override
          public void close() {
            closed.set(true);
          }
        };

    new MeteredReportRenderer(delegate, registry).close();

    assertThat(closed).isTrue();
  }

  @Test
  void should_tag_the_timer_with_the_template_name() {
    var renderer = new MeteredReportRenderer(succeeding(), registry);

    renderer.toBytes(REQUEST);

    assertThat(
            registry
                .get("report.render")
                .tag("template", "orders.jrxml")
                .tag("format", "pdf")
                .tag("outcome", "SUCCESS")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void should_reject_missing_collaborators() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new MeteredReportRenderer(null, registry))
        .withMessage("delegate and registry must not be null");
  }

  private long count(String format, String outcome) {
    return registry
        .get("report.render")
        .tag("format", format)
        .tag("outcome", outcome)
        .timer()
        .count();
  }

  private static ReportRenderer succeeding() {
    return new ReportRenderer() {
      @Override
      public void render(ReportRequest request, OutputStream out) {}

      @Override
      public byte[] toBytes(ReportRequest request) {
        return DOCUMENT;
      }
    };
  }

  private static ReportRenderer failing(RuntimeException failure) {
    return new ReportRenderer() {
      @Override
      public void render(ReportRequest request, OutputStream out) {
        throw failure;
      }

      @Override
      public byte[] toBytes(ReportRequest request) {
        throw failure;
      }
    };
  }
}
