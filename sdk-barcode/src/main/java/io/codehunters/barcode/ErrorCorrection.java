package io.codehunters.barcode;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/** QR Code error correction: the share of the symbol that can be damaged and still be read. */
public enum ErrorCorrection {
  /** About 7%. */
  L(ErrorCorrectionLevel.L),
  /** About 15%, the default. */
  M(ErrorCorrectionLevel.M),
  /** About 25%. */
  Q(ErrorCorrectionLevel.Q),
  /** About 30%. */
  H(ErrorCorrectionLevel.H);

  private final ErrorCorrectionLevel zxing;

  ErrorCorrection(ErrorCorrectionLevel zxing) {
    this.zxing = zxing;
  }

  ErrorCorrectionLevel zxing() {
    return zxing;
  }
}
