package io.codehunters.barcode;

/**
 * Upper bounds that keep a request or an uploaded image from exhausting memory.
 *
 * @param maxWidth widest image rendered, 1 to {@value #MAX_SIDE} pixels
 * @param maxHeight tallest image rendered, 1 to {@value #MAX_SIDE} pixels
 * @param maxDecodePixels largest image decoded ({@code width x height}), 1 to {@value
 *     #MAX_DECODE_PIXELS}; checked from the image header, before its pixels are read
 * @param maxDecodeBytes most memory one decode may take, 1 to {@value #MAX_DECODE_BYTES} bytes;
 *     estimated from the header (bytes per pixel of the decoded image, once downscaled to about
 *     2048 x 2048, plus the JPEG decoder's own buffer)
 */
public record BarcodeLimits(
    int maxWidth, int maxHeight, long maxDecodePixels, long maxDecodeBytes) {

  /** Largest accepted {@code maxWidth} and {@code maxHeight}. */
  public static final int MAX_SIDE = 16_384;

  /** Largest accepted {@code maxDecodePixels}. */
  public static final long MAX_DECODE_PIXELS = 33_554_432L;

  /** Largest accepted {@code maxDecodeBytes}: 1 GB. */
  public static final long MAX_DECODE_BYTES = 1_073_741_824L;

  /** Default {@code maxDecodeBytes}: 128 MB. */
  public static final long DEFAULT_DECODE_BYTES = 134_217_728L;

  /** 4096 x 4096 rendered; 16 megapixels and 128 MB decoded. */
  public static final BarcodeLimits DEFAULT =
      new BarcodeLimits(4096, 4096, 16_777_216L, DEFAULT_DECODE_BYTES);

  /** Validates the ranges. */
  public BarcodeLimits {
    requireBetween("max-width", maxWidth, MAX_SIDE);
    requireBetween("max-height", maxHeight, MAX_SIDE);
    requireBetween("max-decode-pixels", maxDecodePixels, MAX_DECODE_PIXELS);
    requireBetween("max-decode-bytes", maxDecodeBytes, MAX_DECODE_BYTES);
  }

  /**
   * Limits with {@link #DEFAULT_DECODE_BYTES} of decode memory.
   *
   * @param maxWidth widest image rendered
   * @param maxHeight tallest image rendered
   * @param maxDecodePixels largest image decoded
   */
  public BarcodeLimits(int maxWidth, int maxHeight, long maxDecodePixels) {
    this(maxWidth, maxHeight, maxDecodePixels, DEFAULT_DECODE_BYTES);
  }

  private static void requireBetween(String name, long value, long max) {
    if (value < 1 || value > max) {
      throw new IllegalArgumentException(name + " must be between 1 and " + max);
    }
  }
}
