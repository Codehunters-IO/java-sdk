package io.codehunters.jasper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportSettingsTest {

  @Test
  void should_default_to_the_reports_folder_a_cache_and_50_mb_without_virtualization() {
    assertThat(ReportSettings.DEFAULT)
        .isEqualTo(new ReportSettings("reports", 64, 50L * 1024 * 1024, Virtualization.OFF));
  }

  @Test
  void should_bound_fills_to_2000_pages_and_two_minutes_by_default() {
    assertThat(ReportSettings.DEFAULT.fillLimits())
        .isEqualTo(new FillLimits(2_000, Duration.ofMinutes(2)));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1_000_001})
  void should_reject_a_page_limit_outside_its_range(int maxPages) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FillLimits(maxPages, Duration.ofMinutes(1)))
        .withMessage("max-pages must be between 1 and 1000000");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0S", "PT1H0.001S", "PT-1S"})
  void should_reject_a_timeout_outside_its_range(Duration timeout) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FillLimits(100, timeout))
        .withMessage("timeout must be between 1 ms and 1 hour");
  }

  @Test
  void should_reject_missing_fill_limits() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ReportSettings("reports", 64, 1024, null, Virtualization.OFF))
        .withMessage("fill limits must not be null");
  }

  @Test
  void should_resolve_a_template_under_the_base() {
    assertThat(ReportSettings.DEFAULT.resolve("sales/q1.jasper"))
        .isEqualTo("reports/sales/q1.jasper");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "/reports", "../reports", "classpath:reports", "reports/"})
  void should_reject_a_base_that_is_not_a_relative_classpath_folder(String base) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ReportSettings(base, 64, 1024, Virtualization.OFF))
        .withMessage(
            "template-base must be a relative classpath folder made of letters, digits, '.', '_',"
                + " '-' and '/'");
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 10_001})
  void should_reject_a_cache_size_outside_its_range(int cacheSize) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ReportSettings("reports", cacheSize, 1024, Virtualization.OFF))
        .withMessage("cache-size must be between 0 and 10000");
  }

  @ParameterizedTest
  @ValueSource(longs = {0, Integer.MAX_VALUE - 7L})
  void should_reject_an_output_limit_outside_its_range(long maxOutputBytes) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ReportSettings("reports", 64, maxOutputBytes, Virtualization.OFF))
        .withMessage("max-output-size must be between 1 and 2147483639 bytes");
  }

  @Test
  void should_reject_a_missing_virtualization() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ReportSettings("reports", 64, 1024, null))
        .withMessage("virtualization must not be null");
  }

  @Test
  void should_swap_pages_to_the_given_folder() {
    var virtualization = Virtualization.swapFile(10, Path.of("/var/tmp"));

    assertThat(virtualization.enabled()).isTrue();
    assertThat(virtualization.maxPages()).isEqualTo(10);
  }

  @Test
  void should_keep_virtualization_off_in_the_temporary_folder_by_default() {
    assertThat(Virtualization.OFF.enabled()).isFalse();
    assertThat(Virtualization.OFF.directory())
        .isEqualTo(Path.of(System.getProperty("java.io.tmpdir")));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 100_001})
  void should_reject_a_page_count_outside_its_range(int maxPages) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Virtualization.swapFile(maxPages, Path.of("/var/tmp")))
        .withMessage("virtualizer.max-pages must be between 1 and 100000");
  }

  @Test
  void should_reject_a_missing_swap_folder() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Virtualization.swapFile(10, null))
        .withMessage("virtualizer.directory must not be null");
  }
}
