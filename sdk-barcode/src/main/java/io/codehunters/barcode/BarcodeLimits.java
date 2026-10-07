package io.codehunters.barcode;

/**
 * Upper bounds that keep a request or an uploaded image from exhausting memory.
 *
 * @param maxWidth widest image rendered, 1 to {@value #MAX_SIDE} pixels
 * @param maxHeight tallest image rendered, 1 to {@value #MAX_SIDE} pixels
 * @param maxDecodePixels largest image decoded ({@code width x height}), 1 to {@value
 *     #MAX_DECODE_PIXELS}; checked from the image header, before its pixels are read
 */
public record BarcodeLimits(int maxWidth, int maxHeight, long maxDecodePixels) {

  /** Largest accepted {@code maxWidth} and {@code maxHeight}. */
  public static final int MAX_SIDE = 16_384;

  /** Largest accepted {@code maxDecodePixels}: 128 MB of ARGB pixels while decoding. */
  public static final long MAX_DECODE_PIXELS = 33_554_432L;

  /** 4096 x 4096 rendered, 16 megapixels (64 MB of ARGB pixels) decoded. */
  public static final BarcodeLimits DEFAULT = new BarcodeLimits(4096, 4096, 16_777_216L);

  /** Validates the ranges. */
  public BarcodeLimits {
    requireBetween("max-width", maxWidth, MAX_SIDE);
    requireBetween("max-height", maxHeight, MAX_SIDE);
    requireBetween("max-decode-pixels", maxDecodePixels, MAX_DECODE_PIXELS);
  }

  private static void requireBetween(String name, long value, long max) {
    if (value < 1 || value > max) {
      throw new IllegalArgumentException(name + " must be between 1 and " + max);
    }
  }
}
