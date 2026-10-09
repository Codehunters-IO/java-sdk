package io.codehunters.barcode;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import io.codehunters.barcode.internal.ZxingMapping;
import io.codehunters.barcode.internal.read.DecodePlan;
import io.codehunters.barcode.internal.read.Luminance;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.SampleModel;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Finds a barcode of any {@link BarcodeFormat} in a PNG, JPEG, GIF or BMP image. Other formats
 * (TIFF, WBMP, or whatever an ImageIO plugin on the classpath adds) are rejected. Thread-safe.
 *
 * <p>Everything that sizes the decode is read from the image header first: the pixel count is
 * checked against {@link BarcodeLimits#maxDecodePixels()} and the memory the decode would take
 * against {@link BarcodeLimits#maxDecodeBytes()}, so a small file announcing a huge or deep image
 * (a decompression bomb) is rejected cheaply. Images above about 2048 x 2048 pixels are subsampled
 * while they are decoded. ImageIO's file cache is bypassed.
 *
 * <p>Each call holds its decode memory until it returns; bound the number of concurrent calls (a
 * semaphore, or the size of the pool that serves uploads) to bound the total.
 */
public final class BarcodeReader {

  private static final Set<String> FORMATS = Set.of("png", "jpeg", "gif", "bmp");

  /** Every ITF length the renderer accepts (2 to 80 digits), not just ZXing's default 6 to 14. */
  private static final int[] ITF_LENGTHS = IntStream.rangeClosed(1, 40).map(i -> 2 * i).toArray();

  private static final Map<DecodeHintType, Object> HINTS = hints();

  private final BarcodeLimits limits;
  private final Decoder decoder;

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
    this(limits, BarcodeReader::zxing);
  }

  BarcodeReader(BarcodeLimits limits, Decoder decoder) {
    if (limits == null) {
      throw new IllegalArgumentException("limits must not be null");
    }
    if (decoder == null) {
      throw new IllegalArgumentException("decoder must not be null");
    }
    this.limits = limits;
    this.decoder = decoder;
  }

  /**
   * Decodes the first barcode found in the image. The stream is read but not closed.
   *
   * @param image the image bytes
   * @return the barcode, or empty when none is found or it cannot be read
   * @throws BarcodeRejectedException with code {@value BarcodeRejectedException#IMAGE_REJECTED} if
   *     the image exceeds the limits, its format is not supported, or it cannot be read
   * @throws IllegalArgumentException if {@code image} is null
   */
  public Optional<DecodedBarcode> read(InputStream image) {
    if (image == null) {
      throw new IllegalArgumentException("image must not be null");
    }
    return decode(load(image));
  }

  private LuminanceSource load(InputStream image) {
    try (ImageInputStream stream = new MemoryCacheImageInputStream(image)) {
      ImageReader reader = readerFor(stream);
      try {
        reader.setInput(stream, true, true);
        DecodePlan plan = plan(reader);
        requireWithinMemoryLimit(plan);
        return Luminance.of(pixels(reader, plan.subsampling()));
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw unreadable(e);
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
    return supportedReader(stream)
        .orElseThrow(
            () -> BarcodeRejectedException.image("the image format is not supported", null));
  }

  /** Format detection runs each plugin's header parser, which may throw on a crafted file. */
  private static Optional<ImageReader> supportedReader(ImageInputStream stream) {
    try {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
      while (readers.hasNext()) {
        ImageReader reader = readers.next();
        if (format(reader).isPresent()) {
          return Optional.of(reader);
        }
        reader.dispose();
      }
      return Optional.empty();
    } catch (RuntimeException e) {
      throw unreadable(e);
    }
  }

  private static Optional<String> format(ImageReader reader) {
    return Optional.ofNullable(reader.getOriginatingProvider())
        .map(ImageReaderSpi::getFormatNames)
        .stream()
        .flatMap(Arrays::stream)
        .map(name -> name.toLowerCase(Locale.ROOT))
        .filter(FORMATS::contains)
        .findFirst();
  }

  /**
   * Reads the header. The pixel count is checked before the image type is asked for, because some
   * plugins read past the header (PNG walks its chunks) to answer that.
   */
  private DecodePlan plan(ImageReader reader) throws IOException {
    long pixels = header(() -> (long) reader.getWidth(0) * reader.getHeight(0));
    if (pixels > limits.maxDecodePixels()) {
      throw BarcodeRejectedException.image(
          "the image exceeds the limit of " + limits.maxDecodePixels() + " pixels", null);
    }
    return header(
        () ->
            new DecodePlan(
                reader.getWidth(0),
                reader.getHeight(0),
                bytesPerPixel(reader),
                format(reader).filter("jpeg"::equals).isPresent()));
  }

  /** ImageIO plugins signal some malformed headers with runtime exceptions. */
  private static <T> T header(HeaderRead<T> read) throws IOException {
    try {
      return read.get();
    } catch (RuntimeException e) {
      throw unreadable(e);
    }
  }

  /** Bytes per pixel of the image {@code read} allocates: the first type the plugin offers. */
  private static int bytesPerPixel(ImageReader reader) throws IOException {
    SampleModel model = reader.getImageTypes(0).next().getSampleModel();
    int bits = model.getNumDataElements() * DataBuffer.getDataTypeSize(model.getDataType());
    return Math.max(1, (bits + 7) / 8);
  }

  private void requireWithinMemoryLimit(DecodePlan plan) {
    if (plan.bytes() > limits.maxDecodeBytes()) {
      throw BarcodeRejectedException.image(
          "the image exceeds the decode memory limit of " + limits.maxDecodeBytes() + " bytes",
          null);
    }
  }

  /** Decodes the pixels; ImageIO decoders signal some malformed files with runtime exceptions. */
  private static BufferedImage pixels(ImageReader reader, int subsampling) throws IOException {
    ImageReadParam param = reader.getDefaultReadParam();
    param.setSourceSubsampling(subsampling, subsampling, 0, 0);
    try {
      return reader.read(0, param);
    } catch (RuntimeException e) {
      throw unreadable(e);
    }
  }

  private static BarcodeRejectedException unreadable(Exception cause) {
    return BarcodeRejectedException.image("the image could not be read", cause);
  }

  /**
   * ZXing's decoders have thrown runtime exceptions on crafted images; those count as "no barcode",
   * like any other image ZXing cannot read.
   */
  private Optional<DecodedBarcode> decode(LuminanceSource source) {
    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
    try {
      Result result = decoder.decode(bitmap);
      return ZxingMapping.fromZxing(result.getBarcodeFormat())
          .map(format -> new DecodedBarcode(format, result.getText()));
    } catch (ReaderException | RuntimeException e) {
      return Optional.empty();
    }
  }

  private static Result zxing(BinaryBitmap bitmap) throws ReaderException {
    return new MultiFormatReader().decode(bitmap, HINTS);
  }

  /**
   * No character set is forced: symbols this SDK renders carry an ECI, and for third-party symbols
   * without one ZXing guesses the character set better than a fixed UTF-8 would.
   */
  private static Map<DecodeHintType, Object> hints() {
    Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(
        DecodeHintType.POSSIBLE_FORMATS,
        Arrays.stream(BarcodeFormat.values()).map(ZxingMapping::toZxing).toList());
    hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    hints.put(DecodeHintType.ALLOWED_LENGTHS, ITF_LENGTHS);
    return Map.copyOf(hints);
  }

  @FunctionalInterface
  private interface HeaderRead<T> {
    T get() throws IOException;
  }

  /** The ZXing call, replaceable in tests. */
  @FunctionalInterface
  interface Decoder {
    Result decode(BinaryBitmap bitmap) throws ReaderException;
  }
}
