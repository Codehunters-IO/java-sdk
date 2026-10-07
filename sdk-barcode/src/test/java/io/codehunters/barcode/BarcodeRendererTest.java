package io.codehunters.barcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BarcodeRendererTest {

  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G'};

  private final BarcodeRenderer renderer = new BarcodeRenderer();

  @ParameterizedTest
  @CsvSource({
    "QR_CODE, https://codehunters.io/ticket/42, 300, 300",
    "DATA_MATRIX, LOT-2026-0042, 200, 200",
    "PDF_417, Ticket 42, 400, 150",
    "CODE_128, ORDER-42/a, 400, 120",
    "CODE_39, ORDER 42, 400, 120",
    "EAN_13, 400638133393, 400, 120",
    "UPC_A, 03600029145, 400, 120",
    "ITF, 12345678901231, 400, 120",
  })
  void should_render_a_png_of_the_requested_size_for_every_format(
      BarcodeFormat format, String content, int width, int height) {
    byte[] png = renderer.toPng(BarcodeRequest.of(format, content, width, height));

    BufferedImage image = Images.read(png);
    assertThat(png).startsWith(PNG_SIGNATURE);
    assertThat(image.getWidth()).isEqualTo(width);
    assertThat(image.getHeight()).isEqualTo(height);
  }

  @ParameterizedTest
  @CsvSource({"DATA_MATRIX, 3112, 600, 600", "PDF_417, 2686, 1200, 600"})
  void should_render_the_longest_numeric_content_each_2d_format_accepts(
      BarcodeFormat format, int digits, int width, int height) {
    byte[] png = renderer.toPng(BarcodeRequest.of(format, "1".repeat(digits), width, height));

    assertThat(png).startsWith(PNG_SIGNATURE);
  }

  @Test
  void should_render_the_longest_numeric_qr_code_at_low_error_correction() {
    BarcodeRequest request =
        BarcodeRequest.of(BarcodeFormat.QR_CODE, "1".repeat(7089), 600, 600)
            .withErrorCorrection(ErrorCorrection.L);

    assertThat(renderer.toPng(request)).startsWith(PNG_SIGNATURE);
  }

  @Test
  void should_render_without_a_display() {
    byte[] png = renderer.toPng(BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 100, 100));

    assertThat(GraphicsEnvironment.isHeadless()).isTrue();
    assertThat(png).startsWith(PNG_SIGNATURE);
  }

  @Test
  void should_render_a_two_dimensional_svg_that_keeps_its_proportions() {
    String svg = svg(BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 300, 300));

    assertThat(svg)
        .startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\"")
        .contains("width=\"300\" height=\"300\"", "viewBox=\"0 0 29 29\"")
        .contains("preserveAspectRatio=\"xMidYMid meet\"", "shape-rendering=\"crispEdges\"")
        .contains("<rect x=\"4\" y=\"4\" width=\"7\" height=\"1\"/>")
        .endsWith("</svg>");
  }

  @Test
  void should_stretch_the_bars_of_a_linear_svg_to_the_requested_height() {
    String svg = svg(BarcodeRequest.of(BarcodeFormat.CODE_128, "42", 400, 120));

    assertThat(svg)
        .contains("width=\"400\" height=\"120\"", "preserveAspectRatio=\"none\"")
        .containsPattern("viewBox=\"0 0 \\d+ 1\"")
        .contains("<rect x=\"10\" y=\"0\" width=\"2\" height=\"1\"/>");
  }

  @ParameterizedTest
  @CsvSource({"PNG, 101, 50", "PNG, 50, 101", "SVG, 101, 50", "SVG, 50, 101"})
  void should_reject_a_request_larger_than_the_limits(ImageFormat format, int width, int height) {
    BarcodeRenderer limited = new BarcodeRenderer(new BarcodeLimits(100, 100, 10_000));
    BarcodeRequest request = BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", width, height);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> limited.render(request, format, new ByteArrayOutputStream()))
        .withMessage("the requested size exceeds the limit of 100 x 100 pixels");
  }

  @Test
  void should_reject_a_rendered_barcode_larger_than_the_limits() {
    BarcodeRenderer limited = new BarcodeRenderer(new BarcodeLimits(30, 30, 10_000));
    BarcodeRequest request =
        BarcodeRequest.of(BarcodeFormat.QR_CODE, "https://codehunters.io/ticket/42", 10, 10);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> limited.toPng(request))
        .withMessage("the rendered barcode exceeds the limit of 30 x 30 pixels");
  }

  @Test
  void should_reject_content_that_does_not_fit_the_symbol_without_echoing_it() {
    String content = "A".repeat(5000);
    BarcodeRequest request = BarcodeRequest.of(BarcodeFormat.QR_CODE, content, 300, 300);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> renderer.toPng(request))
        .withMessage("the content cannot be encoded as QR_CODE")
        .withNoCause();
  }

  @Test
  void should_report_a_failing_output_stream_as_unchecked() {
    BarcodeRequest request = BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 100, 100);

    assertThatThrownBy(() -> renderer.render(request, ImageFormat.SVG, new FailingOutputStream()))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessage("the barcode could not be written");
  }

  @Test
  void should_report_a_failing_png_output_stream_as_unchecked() {
    BarcodeRequest request = BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 100, 100);

    assertThatThrownBy(() -> renderer.render(request, ImageFormat.PNG, new FailingOutputStream()))
        .isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void should_reject_missing_arguments() {
    BarcodeRequest request = BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 100, 100);
    OutputStream out = new ByteArrayOutputStream();

    assertThatIllegalArgumentException()
        .isThrownBy(() -> renderer.render(null, ImageFormat.PNG, out))
        .withMessage("request, format and output must not be null");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> renderer.render(request, null, out))
        .withMessage("request, format and output must not be null");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> renderer.render(request, ImageFormat.PNG, null))
        .withMessage("request, format and output must not be null");
  }

  @Test
  void should_reject_missing_limits() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new BarcodeRenderer(null))
        .withMessage("limits must not be null");
  }

  private String svg(BarcodeRequest request) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    renderer.render(request, ImageFormat.SVG, out);
    return out.toString(StandardCharsets.UTF_8);
  }

  private static final class FailingOutputStream extends OutputStream {
    @Override
    public void write(int b) throws IOException {
      throw new IOException("disk full");
    }
  }
}
