package io.codehunters.geo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoordinateValidationTest {

  @ParameterizedTest
  @ValueSource(
      doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 90.5, -90.5})
  void should_reject_latitude_when_not_a_finite_value_in_range(double value) {
    assertThatThrownBy(() -> new Latitude(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Latitude must be a number in [-90, 90]");
  }

  @ParameterizedTest
  @ValueSource(
      doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -180.5, 180.5})
  void should_reject_longitude_when_not_a_finite_value_in_range(double value) {
    assertThatThrownBy(() -> new Longitude(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Longitude must be a number in [-180, 180]");
  }
}
