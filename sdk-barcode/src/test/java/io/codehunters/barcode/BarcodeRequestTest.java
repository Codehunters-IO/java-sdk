package io.codehunters.barcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BarcodeRequestTest {

  @ParameterizedTest
  @CsvSource({
    "QR_CODE, https://codehunters.io/ticket/42",
    "DATA_MATRIX, LOT-2026-0042",
    "PDF_417, Ticket 42 — Ñandú",
    "CODE_128, ORDER-42/a",
    "CODE_39, ORDER 42-$/+%.",
    "EAN_13, 400638133393",
    "EAN_13, 4006381333931",
    "UPC_A, 03600029145",
    "UPC_A, 036000291452",
    "ITF, 12345678901231",
  })
  void should_accept_content_when_it_follows_the_format_rules(
      BarcodeFormat format, String content) {
    BarcodeRequest request = BarcodeRequest.of(format, content, 300, 150);

    assertThat(request.content()).isEqualTo(content);
  }

  @ParameterizedTest
  @CsvSource({
    "EAN_13, 40063813X393, EAN-13 content must be 12 or 13 digits",
    "EAN_13, 40063813339, EAN-13 content must be 12 or 13 digits",
    "EAN_13, 4006381333932, EAN-13 check digit does not match",
    "UPC_A, 0360002914, UPC-A content must be 11 or 12 digits",
    "UPC_A, 036000291453, UPC-A check digit does not match",
    "ITF, 1234567, ITF content must be an even number of digits",
    "ITF, 12AB, ITF content must be an even number of digits",
    "CODE_39, order-42, CODE 39 content may only contain",
    "CODE_128, ORDER€42, CODE 128 content may only contain printable ASCII",
  })
  void should_reject_content_without_echoing_it_when_it_breaks_the_format_rules(
      BarcodeFormat format, String content, String message) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> BarcodeRequest.of(format, content, 300, 150))
        .withMessageContaining(message)
        .withMessageNotContaining(content);
  }

  @ParameterizedTest
  @CsvSource({"CODE_128, 81", "CODE_39, 81", "ITF, 82", "QR_CODE, 7090", "DATA_MATRIX, 3113"})
  void should_reject_content_when_it_is_longer_than_the_format_allows(
      BarcodeFormat format, int length) {
    String content = "1".repeat(length);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> BarcodeRequest.of(format, content, 300, 150))
        .withMessageContaining("at most");
  }

  @Test
  void should_reject_pdf417_content_when_it_is_longer_than_the_format_allows() {
    String content = "1".repeat(2687);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> BarcodeRequest.of(BarcodeFormat.PDF_417, content, 300, 150))
        .withMessageContaining("at most 2686");
  }

  @Test
  void should_reject_empty_content() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> BarcodeRequest.of(BarcodeFormat.QR_CODE, "", 300, 300))
        .withMessage("content must not be empty");
  }

  @Test
  void should_reject_null_content() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> BarcodeRequest.of(BarcodeFormat.QR_CODE, null, 300, 300))
        .withMessage("content must not be empty");
  }

  @Test
  void should_reject_a_missing_format() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> BarcodeRequest.of(null, "42", 300, 300))
        .withMessage("format must not be null");
  }

  @Test
  void should_reject_a_missing_error_correction() {
    BarcodeRequest request = BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 300, 300);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> request.withErrorCorrection(null))
        .withMessage("errorCorrection must not be null");
  }

  @ParameterizedTest
  @CsvSource({"0, 100", "100, 0", "-1, 100"})
  void should_reject_sizes_below_one_pixel(int width, int height) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", width, height))
        .withMessage("width and height must be at least 1 pixel");
  }

  @ParameterizedTest
  @CsvSource({"-1", "257"})
  void should_reject_margins_outside_the_allowed_range(int margin) {
    BarcodeRequest request = BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 300, 300);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> request.withMargin(margin))
        .withMessage("margin must be between 0 and 256");
  }

  @ParameterizedTest
  @CsvSource({"QR_CODE, 4", "DATA_MATRIX, 4", "PDF_417, 4", "CODE_128, 10", "ITF, 10"})
  void should_default_to_the_quiet_zone_of_the_format(BarcodeFormat format, int margin) {
    BarcodeRequest request = BarcodeRequest.of(format, "12345678901231", 300, 150);

    assertThat(request.margin()).isEqualTo(margin);
  }

  @Test
  void should_default_to_medium_error_correction() {
    BarcodeRequest request = BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 300, 300);

    assertThat(request.errorCorrection()).isEqualTo(ErrorCorrection.M);
  }

  @Test
  void should_copy_the_request_with_another_margin_and_error_correction() {
    BarcodeRequest request =
        BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 300, 300)
            .withMargin(0)
            .withErrorCorrection(ErrorCorrection.H);

    assertThat(request.margin()).isZero();
    assertThat(request.errorCorrection()).isEqualTo(ErrorCorrection.H);
  }

  @Test
  void should_hide_the_content_when_printed() {
    BarcodeRequest request = BarcodeRequest.of(BarcodeFormat.QR_CODE, "secret-ticket-42", 300, 300);

    assertThat(request.toString())
        .doesNotContain("secret-ticket-42")
        .contains("content=<16 chars>", "format=QR_CODE", "width=300");
  }

  @Test
  void should_tell_one_and_two_dimensional_formats_apart() {
    assertThat(BarcodeFormat.QR_CODE.isTwoDimensional()).isTrue();
    assertThat(BarcodeFormat.EAN_13.isTwoDimensional()).isFalse();
  }
}
