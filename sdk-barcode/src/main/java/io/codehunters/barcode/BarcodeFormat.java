package io.codehunters.barcode;

import java.util.Arrays;
import java.util.Optional;

/** The symbologies the SDK renders and decodes. */
public enum BarcodeFormat {
  /** QR Code, 2D. */
  QR_CODE(true, com.google.zxing.BarcodeFormat.QR_CODE),
  /** Data Matrix (ECC 200), 2D. */
  DATA_MATRIX(true, com.google.zxing.BarcodeFormat.DATA_MATRIX),
  /** PDF417, stacked 2D. */
  PDF_417(true, com.google.zxing.BarcodeFormat.PDF_417),
  /** Code 128, linear, printable ASCII. */
  CODE_128(false, com.google.zxing.BarcodeFormat.CODE_128),
  /** Code 39, linear, upper-case letters, digits and {@code - . $ / + %} and space. */
  CODE_39(false, com.google.zxing.BarcodeFormat.CODE_39),
  /** EAN-13, linear, 12 digits plus a check digit. */
  EAN_13(false, com.google.zxing.BarcodeFormat.EAN_13),
  /** UPC-A, linear, 11 digits plus a check digit. */
  UPC_A(false, com.google.zxing.BarcodeFormat.UPC_A),
  /** Interleaved 2 of 5, linear, an even number of digits. */
  ITF(false, com.google.zxing.BarcodeFormat.ITF);

  private final boolean twoDimensional;
  private final com.google.zxing.BarcodeFormat zxing;

  BarcodeFormat(boolean twoDimensional, com.google.zxing.BarcodeFormat zxing) {
    this.twoDimensional = twoDimensional;
    this.zxing = zxing;
  }

  /**
   * Tells matrix and stacked symbols from linear ones.
   *
   * @return {@code true} for QR Code, Data Matrix and PDF417
   */
  public boolean isTwoDimensional() {
    return twoDimensional;
  }

  com.google.zxing.BarcodeFormat zxing() {
    return zxing;
  }

  static Optional<BarcodeFormat> of(com.google.zxing.BarcodeFormat zxing) {
    return Arrays.stream(values()).filter(format -> format.zxing == zxing).findFirst();
  }
}
