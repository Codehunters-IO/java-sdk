package io.codehunters.barcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BarcodeLimitsTest {

  @Test
  void should_default_to_4096_pixels_per_side_16_megapixels_and_128_mb_to_decode() {
    assertThat(BarcodeLimits.DEFAULT)
        .isEqualTo(new BarcodeLimits(4096, 4096, 16_777_216, 134_217_728));
  }

  @Test
  void should_default_the_decode_memory_when_only_pixels_are_given() {
    BarcodeLimits limits = new BarcodeLimits(100, 100, 10_000);

    assertThat(limits.maxDecodeBytes()).isEqualTo(BarcodeLimits.DEFAULT_DECODE_BYTES);
  }

  @ParameterizedTest
  @CsvSource({"0", "1073741825"})
  void should_reject_a_decode_memory_limit_outside_its_range(long maxDecodeBytes) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new BarcodeLimits(100, 100, 10_000, maxDecodeBytes))
        .withMessage("max-decode-bytes must be between 1 and 1073741824");
  }

  @ParameterizedTest
  @CsvSource({
    "0, 100, 100, max-width must be between 1 and 16384",
    "16385, 100, 100, max-width must be between 1 and 16384",
    "100, 0, 100, max-height must be between 1 and 16384",
    "100, 16385, 100, max-height must be between 1 and 16384",
    "100, 100, 0, max-decode-pixels must be between 1 and 33554432",
    "100, 100, 33554433, max-decode-pixels must be between 1 and 33554432",
  })
  void should_reject_limits_outside_their_range(
      int maxWidth, int maxHeight, long maxDecodePixels, String message) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new BarcodeLimits(maxWidth, maxHeight, maxDecodePixels))
        .withMessage(message);
  }

  @Test
  void should_accept_the_largest_limits() {
    BarcodeLimits limits = new BarcodeLimits(16384, 16384, 33_554_432, 1_073_741_824);

    assertThat(limits.maxDecodePixels()).isEqualTo(33_554_432);
    assertThat(limits.maxDecodeBytes()).isEqualTo(1_073_741_824);
  }
}
