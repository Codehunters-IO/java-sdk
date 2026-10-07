package io.codehunters.barcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BarcodeReaderTest {

  private final BarcodeRenderer renderer = new BarcodeRenderer();
  private final BarcodeReader reader = new BarcodeReader();

  @ParameterizedTest
  @CsvSource({
    "QR_CODE, https://codehunters.io/ticket/42 — Ñandú, 300, 300",
    "DATA_MATRIX, LOT-2026-0042, 200, 200",
    "PDF_417, Ticket 42, 600, 200",
    "CODE_128, ORDER-42/a, 400, 120",
    "CODE_39, ORDER 42, 400, 120",
    "EAN_13, 4006381333931, 400, 120",
    "UPC_A, 036000291452, 400, 120",
    "ITF, 12345678901231, 400, 120",
  })
  void should_decode_what_the_renderer_wrote(
      BarcodeFormat format, String content, int width, int height) {
    byte[] png = renderer.toPng(BarcodeRequest.of(format, content, width, height));

    assertThat(reader.read(new ByteArrayInputStream(png)))
        .contains(new DecodedBarcode(format, content));
  }

  @Test
  void should_complete_the_check_digit_of_a_twelve_digit_ean13() {
    byte[] png = renderer.toPng(BarcodeRequest.of(BarcodeFormat.EAN_13, "400638133393", 400, 120));

    assertThat(reader.read(new ByteArrayInputStream(png)))
        .contains(new DecodedBarcode(BarcodeFormat.EAN_13, "4006381333931"));
  }

  @Test
  void should_find_nothing_in_an_image_without_a_barcode() {
    byte[] png = Images.whitePng(200, 200);

    assertThat(reader.read(new ByteArrayInputStream(png))).isEmpty();
  }

  @Test
  void should_reject_an_image_above_the_pixel_limit() {
    BarcodeReader limited = new BarcodeReader(new BarcodeLimits(100, 100, 10_000));
    byte[] png = Images.whitePng(101, 100);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> limited.read(new ByteArrayInputStream(png)))
        .withMessage("the image exceeds the limit of 10000 pixels");
  }

  @Test
  void should_reject_a_decompression_bomb_from_its_header_before_reading_pixels() {
    byte[] bomb = Images.pngHeaderOnly(100_000, 100_000);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> reader.read(new ByteArrayInputStream(bomb)))
        .withMessage("the image exceeds the limit of 16777216 pixels");
  }

  @Test
  void should_reject_bytes_that_are_not_an_image() {
    InputStream text = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> reader.read(text))
        .withMessage("the image format is not supported");
  }

  @Test
  void should_reject_an_image_whose_pixels_are_missing() {
    byte[] truncated = Images.pngHeaderOnly(10, 10);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> reader.read(new ByteArrayInputStream(truncated)))
        .withMessage("the image could not be read");
  }

  @Test
  void should_reject_a_failing_input_stream() {
    InputStream failing =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("connection reset");
          }
        };

    assertThatIllegalArgumentException()
        .isThrownBy(() -> reader.read(failing))
        .withMessage("the image could not be read");
  }

  @Test
  void should_reject_missing_arguments() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> reader.read(null))
        .withMessage("image must not be null");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new BarcodeReader(null))
        .withMessage("limits must not be null");
  }

  @Test
  void should_hide_the_decoded_content_when_printed() {
    DecodedBarcode decoded = new DecodedBarcode(BarcodeFormat.QR_CODE, "secret-ticket-42");

    assertThat(decoded.toString())
        .doesNotContain("secret-ticket-42")
        .isEqualTo("DecodedBarcode[format=QR_CODE, content=<16 chars>]");
  }

  @Test
  void should_reject_a_decoded_barcode_without_format_or_content() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new DecodedBarcode(null, "42"))
        .withMessage("format and content must not be null");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new DecodedBarcode(BarcodeFormat.QR_CODE, null))
        .withMessage("format and content must not be null");
  }
}
