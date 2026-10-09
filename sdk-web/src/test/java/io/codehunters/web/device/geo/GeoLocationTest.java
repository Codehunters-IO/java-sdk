package io.codehunters.web.device.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeoLocationTest {

  @Test
  void should_hold_a_location_when_coordinates_are_in_range() {
    var location = new GeoLocation("CO", "Antioquia", "Medellín", 6.2442, -75.5812);

    assertThat(location.countryCode()).isEqualTo("CO");
  }

  @ParameterizedTest
  @CsvSource({"90.1, 0", "-90.1, 0", "0, 180.1", "0, -180.1", "NaN, 0", "0, Infinity"})
  void should_reject_coordinates_when_out_of_range_or_not_finite(
      double latitude, double longitude) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new GeoLocation("CO", "", "", latitude, longitude))
        .withMessage("coordinates are out of range");
  }

  @Test
  void should_reject_null_names() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new GeoLocation(null, "", "", 0, 0))
        .withMessage("location names must not be null; use an empty string when unknown");
  }
}
