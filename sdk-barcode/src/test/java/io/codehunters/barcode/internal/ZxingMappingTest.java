package io.codehunters.barcode.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.codehunters.barcode.BarcodeFormat;
import io.codehunters.barcode.ErrorCorrection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ZxingMappingTest {

  @ParameterizedTest
  @EnumSource(BarcodeFormat.class)
  void should_map_back_to_the_same_format_when_round_tripping_through_zxing(BarcodeFormat format) {
    assertThat(ZxingMapping.fromZxing(ZxingMapping.toZxing(format))).contains(format);
  }

  @Test
  void should_map_qr_code_to_zxing_qr_code_when_converting_a_format() {
    assertThat(ZxingMapping.toZxing(BarcodeFormat.QR_CODE))
        .isEqualTo(com.google.zxing.BarcodeFormat.QR_CODE);
  }

  @Test
  void should_return_empty_when_zxing_format_is_not_supported() {
    assertThat(ZxingMapping.fromZxing(com.google.zxing.BarcodeFormat.AZTEC)).isEmpty();
  }

  @Test
  void should_map_high_error_correction_to_zxing_level_h_when_converting_a_level() {
    assertThat(ZxingMapping.toZxing(ErrorCorrection.H)).isEqualTo(ErrorCorrectionLevel.H);
  }
}
