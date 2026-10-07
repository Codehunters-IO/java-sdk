package io.codehunters.barcode;

/** The image formats {@link BarcodeRenderer} writes. */
public enum ImageFormat {
  /** A black and white PNG of the requested size. */
  PNG,
  /** A scalable SVG with one {@code rect} per run of dark modules. */
  SVG
}
