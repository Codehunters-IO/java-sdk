package io.codehunters.barcode;

/**
 * A barcode found in an image.
 *
 * @param format the symbology
 * @param content the decoded text; never printed by {@link #toString()}
 */
public record DecodedBarcode(BarcodeFormat format, String content) {

  /** Validates the fields. */
  public DecodedBarcode {
    if (format == null || content == null) {
      throw new IllegalArgumentException("format and content must not be null");
    }
  }

  @Override
  public String toString() {
    return "DecodedBarcode[format=%s, content=<%d chars>]".formatted(format, content.length());
  }
}
