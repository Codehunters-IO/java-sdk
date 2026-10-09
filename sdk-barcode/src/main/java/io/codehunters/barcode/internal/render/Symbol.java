package io.codehunters.barcode.internal.render;

import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.pdf417.encoder.Dimensions;
import io.codehunters.barcode.BarcodeFormat;
import io.codehunters.barcode.BarcodeRequest;
import io.codehunters.barcode.internal.ZxingMapping;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * An encoded symbol in modules, with its quiet zone. ZXing is asked for the bare symbol so the
 * margin means the same for every format: modules on each side. A linear symbol has one row, which
 * the writers stretch to the image height.
 */
public final class Symbol {

  /** The whole PDF417 range (1 to 30 data columns, 3 to 90 rows); ZXing stops at 30 rows. */
  private static final Dimensions PDF417_DIMENSIONS = new Dimensions(1, 30, 3, 90);

  private final BitMatrix modules;
  private final int margin;
  private final boolean linear;

  private Symbol(BitMatrix modules, int margin, boolean linear) {
    this.modules = modules;
    this.margin = margin;
    this.linear = linear;
  }

  public static Symbol encode(BarcodeRequest request) {
    BarcodeFormat format = request.format();
    try {
      BitMatrix modules =
          new MultiFormatWriter()
              .encode(request.content(), ZxingMapping.toZxing(format), 0, 0, hints(request));
      return new Symbol(modules, request.margin(), !format.isTwoDimensional());
    } catch (WriterException | IllegalArgumentException e) {
      // ZXing messages may quote the content; the cause is dropped on purpose.
      throw new IllegalArgumentException("the content cannot be encoded as " + format);
    }
  }

  int columns() {
    return modules.getWidth() + 2 * margin;
  }

  int rows() {
    return linear ? 1 : modules.getHeight() + 2 * margin;
  }

  boolean isLinear() {
    return linear;
  }

  /** Horizontal runs of dark modules, in symbol coordinates (quiet zone included). */
  List<Run> runs() {
    List<Run> runs = new ArrayList<>();
    for (int y = 0; y < (linear ? 1 : modules.getHeight()); y++) {
      addRuns(runs, y);
    }
    return runs;
  }

  private void addRuns(List<Run> runs, int y) {
    int x = 0;
    while (x < modules.getWidth()) {
      int start = x;
      while (x < modules.getWidth() && modules.get(x, y)) {
        x++;
      }
      if (x > start) {
        runs.add(new Run(start + margin, linear ? 0 : y + margin, x - start));
      }
      x = Math.max(x, start + 1);
    }
  }

  /**
   * UTF-8 with an ECI where the symbology has one. Data Matrix needs its compact (minimal) encoder
   * for that: the default one ignores the character set and writes ISO-8859-1.
   */
  private static Map<EncodeHintType, Object> hints(BarcodeRequest request) {
    Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
    hints.put(EncodeHintType.MARGIN, 0);
    hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
    switch (request.format()) {
      case QR_CODE ->
          hints.put(
              EncodeHintType.ERROR_CORRECTION, ZxingMapping.toZxing(request.errorCorrection()));
      case DATA_MATRIX -> hints.put(EncodeHintType.DATA_MATRIX_COMPACT, Boolean.TRUE);
      case PDF_417 -> hints.put(EncodeHintType.PDF417_DIMENSIONS, PDF417_DIMENSIONS);
      default -> {}
    }
    return hints;
  }

  /** {@code length} dark modules starting at {@code (x, y)}. */
  record Run(int x, int y, int length) {}
}
