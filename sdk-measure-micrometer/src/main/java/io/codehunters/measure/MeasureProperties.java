package io.codehunters.measure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the measure SDK, bound from the {@code codehunters.measure} prefix.
 *
 * @param enabled whether {@link Measured} methods are measured and validated
 * @param maxTagValues the distinct values each expression tag may take on a meter; further values
 *     are recorded as {@code other}
 */
@ConfigurationProperties(prefix = "codehunters.measure")
public record MeasureProperties(
    @DefaultValue("true") boolean enabled, @DefaultValue("100") int maxTagValues) {

  /**
   * Validates the settings.
   *
   * @throws IllegalArgumentException if {@code maxTagValues} is less than 1
   */
  public MeasureProperties {
    if (maxTagValues < 1) {
      throw new IllegalArgumentException("codehunters.measure.max-tag-values must be at least 1");
    }
  }
}
