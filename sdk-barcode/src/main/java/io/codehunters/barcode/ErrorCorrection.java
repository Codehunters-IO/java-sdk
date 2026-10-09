package io.codehunters.barcode;

/** QR Code error correction: the share of the symbol that can be damaged and still be read. */
public enum ErrorCorrection {
  /** About 7%. */
  L,
  /** About 15%, the default. */
  M,
  /** About 25%. */
  Q,
  /** About 30%. */
  H
}
