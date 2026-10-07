package io.codehunters.barcode;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Finds a barcode of any {@link BarcodeFormat} in an image (PNG, JPEG, GIF, BMP: whatever ImageIO
 * reads). Thread-safe.
 *
 * <p>The image's dimensions are read from its header and checked against {@link
 * BarcodeLimits#maxDecodePixels()} before any pixel is decoded, so a small file announcing a huge
 * image (a decompression bomb) is rejected cheaply. ImageIO's file cache is bypassed.
 */
public final class BarcodeReader {

  private static final Map<DecodeHintType, Object> HINTS = hints();

  private final BarcodeLimits limits;

  /** A reader with {@link BarcodeLimits#DEFAULT}. */
  public BarcodeReader() {
    this(BarcodeLimits.DEFAULT);
  }

  /**
   * A reader with the given limits.
   *
   * @param limits the largest image it decodes
   */
  public BarcodeReader(BarcodeLimits limits) {
    if (limits == null) {
      throw new IllegalArgumentException("limits must not be null");
    }
    this.limits = limits;
  }

  /**
   * Decodes the first barcode found in the image. The stream is read but not closed.
   *
   * @param image the image bytes
   * @return the barcode, or empty when none is found or it cannot be read
   * @throws IllegalArgumentException if the image exceeds the pixel limit, its format is not
   *     supported, or it cannot be read
   */
  public Optional<DecodedBarcode> read(InputStream image) {
    if (image == null) {
      throw new IllegalArgumentException("image must not be null");
    }
    return decode(load(image));
  }

  private BufferedImage load(InputStream image) {
    try (ImageInputStream stream = new MemoryCacheImageInputStream(image)) {
      ImageReader reader = readerFor(stream);
      try {
        reader.setInput(stream, true, true);
        requireWithinLimit((long) reader.getWidth(0) * reader.getHeight(0));
        return reader.read(0);
      } finally {
        reader.dispose();
      }
    } catch (IOException | IllegalStateException | IndexOutOfBoundsException e) {
      throw new IllegalArgumentException("the image could not be read", e);
    }
  }

  /**
   * Picks the ImageIO reader for the stream. The first byte is read up front because ImageIO's
   * format detection swallows read failures, which would report a broken stream as an unsupported
   * format.
   */
  private static ImageReader readerFor(ImageInputStream stream) throws IOException {
    stream.mark();
    stream.read();
    stream.reset();
    Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
    if (!readers.hasNext()) {
      throw new IllegalArgumentException("the image format is not supported");
    }
    return readers.next();
  }

  private void requireWithinLimit(long pixels) {
    if (pixels > limits.maxDecodePixels()) {
      throw new IllegalArgumentException(
          "the image exceeds the limit of " + limits.maxDecodePixels() + " pixels");
    }
  }

  private static Optional<DecodedBarcode> decode(BufferedImage image) {
    int width = image.getWidth();
    int height = image.getHeight();
    int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
    BinaryBitmap bitmap =
        new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
    try {
      Result result = new MultiFormatReader().decode(bitmap, HINTS);
      return BarcodeFormat.of(result.getBarcodeFormat())
          .map(format -> new DecodedBarcode(format, result.getText()));
    } catch (ReaderException e) {
      return Optional.empty();
    }
  }

  private static Map<DecodeHintType, Object> hints() {
    Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(
        DecodeHintType.POSSIBLE_FORMATS,
        Arrays.stream(BarcodeFormat.values()).map(BarcodeFormat::zxing).toList());
    hints.put(DecodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
    hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    return Map.copyOf(hints);
  }
}
