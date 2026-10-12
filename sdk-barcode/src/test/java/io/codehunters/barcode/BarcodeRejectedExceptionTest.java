package io.codehunters.barcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.errors.ClientInputException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class BarcodeRejectedExceptionTest {

  private static final String IMAGE_REJECTED = "BARCODE_IMAGE_REJECTED";
  private static final String CONTENT_REJECTED = "BARCODE_CONTENT_REJECTED";

  private final BarcodeReader reader = new BarcodeReader();
  private final BarcodeRenderer renderer = new BarcodeRenderer();

  @Test
  void should_name_the_codes_it_carries() {
    assertThat(BarcodeRejectedException.IMAGE_REJECTED).isEqualTo(IMAGE_REJECTED);
    assertThat(BarcodeRejectedException.CONTENT_REJECTED).isEqualTo(CONTENT_REJECTED);
  }

  @Test
  void should_be_client_input_so_the_web_layer_answers_400() {
    InputStream text = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> reader.read(text))
        .isInstanceOf(BarcodeRejectedException.class)
        .isInstanceOf(ClientInputException.class)
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_an_unsupported_image_format_with_the_image_code() {
    InputStream text = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> reader.read(text))
        .hasFieldOrPropertyWithValue("code", IMAGE_REJECTED)
        .hasMessage("the image format is not supported");
  }

  @Test
  void should_reject_an_image_above_the_pixel_limit_with_the_image_code() {
    byte[] bomb = Images.pngHeaderOnly(100_000, 100_000);

    assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(bomb)))
        .hasFieldOrPropertyWithValue("code", IMAGE_REJECTED);
  }

  @Test
  void should_reject_an_image_above_the_memory_limit_with_the_image_code() {
    BarcodeReader limited = new BarcodeReader(new BarcodeLimits(4096, 4096, 16_777_216, 1_000_000));
    byte[] png = Images.rgba16Png(512, 512);

    assertThatThrownBy(() -> limited.read(new ByteArrayInputStream(png)))
        .hasFieldOrPropertyWithValue("code", IMAGE_REJECTED);
  }

  @Test
  void should_reject_an_unreadable_image_with_the_image_code() {
    byte[] truncated = Images.pngHeaderOnly(10, 10);

    assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(truncated)))
        .hasFieldOrPropertyWithValue("code", IMAGE_REJECTED)
        .hasMessage("the image could not be read");
  }

  @ParameterizedTest
  @CsvSource({"ITF, 12AB", "CODE_39, order-42", "EAN_13, 4006381333932", "CODE_128, 'ORDER€42'"})
  void should_reject_content_that_breaks_the_format_rules_with_the_content_code(
      BarcodeFormat format, String content) {
    assertThatThrownBy(() -> BarcodeRequest.of(format, content, 300, 150))
        .isInstanceOf(BarcodeRejectedException.class)
        .hasFieldOrPropertyWithValue("code", CONTENT_REJECTED);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void should_reject_missing_content_with_the_content_code(String content) {
    assertThatThrownBy(() -> BarcodeRequest.of(BarcodeFormat.QR_CODE, content, 300, 300))
        .hasFieldOrPropertyWithValue("code", CONTENT_REJECTED);
  }

  @Test
  void should_reject_a_size_or_margin_out_of_range_with_the_content_code() {
    BarcodeRequest request = BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 300, 300);

    assertThatThrownBy(() -> BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 0, 300))
        .hasFieldOrPropertyWithValue("code", CONTENT_REJECTED);
    assertThatThrownBy(() -> request.withMargin(-1))
        .hasFieldOrPropertyWithValue("code", CONTENT_REJECTED);
  }

  @Test
  void should_reject_content_that_does_not_fit_the_symbol_with_the_content_code() {
    BarcodeRequest request = BarcodeRequest.of(BarcodeFormat.QR_CODE, "A".repeat(5000), 300, 300);

    assertThatThrownBy(() -> renderer.toPng(request))
        .hasFieldOrPropertyWithValue("code", CONTENT_REJECTED)
        .hasNoCause();
  }

  @Test
  void should_reject_a_request_above_the_size_limits_with_the_content_code() {
    BarcodeRenderer limited = new BarcodeRenderer(new BarcodeLimits(100, 100, 10_000));
    BarcodeRequest request = BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 300, 300);

    assertThatThrownBy(() -> limited.render(request, ImageFormat.SVG, new ByteArrayOutputStream()))
        .hasFieldOrPropertyWithValue("code", CONTENT_REJECTED);
  }

  @Test
  void should_keep_a_missing_argument_a_programming_error_not_client_input() {
    assertThatThrownBy(() -> reader.read(null))
        .isInstanceOf(IllegalArgumentException.class)
        .isNotInstanceOf(ClientInputException.class);
    assertThatThrownBy(() -> BarcodeRequest.of(null, "42", 300, 300))
        .isNotInstanceOf(ClientInputException.class);
    assertThatThrownBy(() -> renderer.render(null, ImageFormat.PNG, new ByteArrayOutputStream()))
        .isNotInstanceOf(ClientInputException.class);
  }
}
