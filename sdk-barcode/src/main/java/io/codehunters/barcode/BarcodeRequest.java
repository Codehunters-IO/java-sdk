package io.codehunters.barcode;

import io.codehunters.barcode.internal.render.ContentRules;

/**
 * What to render. Content is checked against the rules of its format on construction; whether it
 * fits the symbol (QR, Data Matrix and PDF417 capacity) is checked when rendering. Text is encoded
 * as UTF-8.
 *
 * @param format the symbology
 * @param content the text to encode; never printed by {@link #toString()}
 * @param width requested image width in pixels; a symbol that needs more is rendered wider
 * @param height requested image height in pixels; a symbol that needs more is rendered taller
 * @param margin quiet zone on each side, in modules, 0 to {@value #MAX_MARGIN}
 * @param errorCorrection QR Code error correction; ignored by the other formats
 */
public record BarcodeRequest(
    BarcodeFormat format,
    String content,
    int width,
    int height,
    int margin,
    ErrorCorrection errorCorrection) {

  /** Largest accepted margin, in modules. */
  public static final int MAX_MARGIN = 256;

  /** Validates the request. */
  public BarcodeRequest {
    if (format == null) {
      throw new IllegalArgumentException("format must not be null");
    }
    if (errorCorrection == null) {
      throw new IllegalArgumentException("errorCorrection must not be null");
    }
    if (content == null || content.isEmpty()) {
      throw BarcodeRejectedException.content("content must not be empty");
    }
    requireSize(width, height, margin);
    ContentRules.check(format, content);
  }

  /**
   * A request with the format's usual quiet zone (4 modules for 2D symbols, 10 for linear ones) and
   * {@link ErrorCorrection#M}.
   *
   * @param format the symbology
   * @param content the text to encode
   * @param width requested image width in pixels
   * @param height requested image height in pixels
   * @return the request
   */
  public static BarcodeRequest of(BarcodeFormat format, String content, int width, int height) {
    int margin = format != null && format.isTwoDimensional() ? 4 : 10;
    return new BarcodeRequest(format, content, width, height, margin, ErrorCorrection.M);
  }

  /**
   * Copies this request with another quiet zone.
   *
   * @param margin modules on each side, 0 to {@value #MAX_MARGIN}
   * @return the new request
   */
  public BarcodeRequest withMargin(int margin) {
    return new BarcodeRequest(format, content, width, height, margin, errorCorrection);
  }

  /**
   * Copies this request with another QR Code error correction.
   *
   * @param errorCorrection the level
   * @return the new request
   */
  public BarcodeRequest withErrorCorrection(ErrorCorrection errorCorrection) {
    return new BarcodeRequest(format, content, width, height, margin, errorCorrection);
  }

  @Override
  public String toString() {
    return "BarcodeRequest[format=%s, content=<%d chars>, width=%d, height=%d, margin=%d, errorCorrection=%s]"
        .formatted(format, content.length(), width, height, margin, errorCorrection);
  }

  private static void requireSize(int width, int height, int margin) {
    if (width < 1 || height < 1) {
      throw BarcodeRejectedException.content("width and height must be at least 1 pixel");
    }
    if (margin < 0 || margin > MAX_MARGIN) {
      throw BarcodeRejectedException.content("margin must be between 0 and " + MAX_MARGIN);
    }
  }
}
