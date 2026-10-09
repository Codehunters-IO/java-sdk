package io.codehunters.barcode.internal.read;

/**
 * How an image will be decoded, worked out from its header before any pixel is read.
 *
 * <p>Images above {@link #TARGET_PIXELS} are subsampled while they are decoded, which bounds both
 * memory and the barcode search: a barcode that survives being shown at 2048 x 2048 still decodes.
 * The memory estimate counts the decoded image at its real depth (a 16-bit RGBA PNG takes 8 bytes
 * per pixel, not 4), the grey copy handed to ZXing, and, for JPEG, the decoder's own buffer, which
 * covers the whole source image whatever the subsampling.
 *
 * @param width source width from the header
 * @param height source height from the header
 * @param bytesPerPixel bytes per pixel of the image ImageIO allocates
 * @param jpeg whether the image is a JPEG
 */
public record DecodePlan(int width, int height, int bytesPerPixel, boolean jpeg) {

  /** Images are subsampled down to about this many pixels: 2048 x 2048. */
  static final long TARGET_PIXELS = 4_194_304L;

  private static final int GREY_BYTES_PER_PIXEL = 1;

  /** A progressive JPEG keeps its DCT coefficients: up to 2 bytes per sample, 3 samples. */
  private static final int JPEG_BUFFER_BYTES_PER_PIXEL = 6;

  long sourcePixels() {
    return (long) width * height;
  }

  /** Keeps one pixel in {@code factor x factor}. */
  public int subsampling() {
    return Math.max(1, (int) Math.ceil(Math.sqrt((double) sourcePixels() / TARGET_PIXELS)));
  }

  long decodedPixels() {
    int factor = subsampling();
    return (long) Math.ceilDiv(width, factor) * Math.ceilDiv(height, factor);
  }

  public long bytes() {
    long decoded = decodedPixels() * (bytesPerPixel + GREY_BYTES_PER_PIXEL);
    return jpeg ? decoded + sourcePixels() * JPEG_BUFFER_BYTES_PER_PIXEL : decoded;
  }
}
