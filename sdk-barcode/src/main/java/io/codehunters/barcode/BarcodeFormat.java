package io.codehunters.barcode;

/** The symbologies the SDK renders and decodes. */
public enum BarcodeFormat {
  /** QR Code, 2D. */
  QR_CODE(true),
  /** Data Matrix (ECC 200), 2D. */
  DATA_MATRIX(true),
  /** PDF417, stacked 2D. */
  PDF_417(true),
  /** Code 128, linear, printable ASCII. */
  CODE_128(false),
  /** Code 39, linear, upper-case letters, digits and {@code - . $ / + %} and space. */
  CODE_39(false),
  /** EAN-13, linear, 12 digits plus a check digit. */
  EAN_13(false),
  /** UPC-A, linear, 11 digits plus a check digit. */
  UPC_A(false),
  /** Interleaved 2 of 5, linear, an even number of digits. */
  ITF(false);

  private final boolean twoDimensional;

  BarcodeFormat(boolean twoDimensional) {
    this.twoDimensional = twoDimensional;
  }

  /**
   * Tells matrix and stacked symbols from linear ones.
   *
   * @return {@code true} for QR Code, Data Matrix and PDF417
   */
  public boolean isTwoDimensional() {
    return twoDimensional;
  }
}
