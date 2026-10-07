package io.codehunters.barcode.config;

import io.codehunters.barcode.BarcodeLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the barcode SDK, bound from the {@code codehunters.barcode} prefix. Invalid
 * values fail at startup.
 *
 * @param enabled whether the SDK auto-configuration is active
 * @param maxWidth widest image rendered, 1 to 16384 pixels
 * @param maxHeight tallest image rendered, 1 to 16384 pixels
 * @param maxDecodePixels largest image decoded ({@code width x height}), 1 to 33554432; checked
 *     from the image header before its pixels are read
 */
@ConfigurationProperties(prefix = "codehunters.barcode")
public record BarcodeProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("4096") int maxWidth,
    @DefaultValue("4096") int maxHeight,
    @DefaultValue("16777216") long maxDecodePixels) {

  /** Validates the limits. */
  public BarcodeProperties {
    try {
      new BarcodeLimits(maxWidth, maxHeight, maxDecodePixels);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("codehunters.barcode." + e.getMessage());
    }
  }

  /**
   * The configured limits.
   *
   * @return the limits
   */
  public BarcodeLimits limits() {
    return new BarcodeLimits(maxWidth, maxHeight, maxDecodePixels);
  }
}
