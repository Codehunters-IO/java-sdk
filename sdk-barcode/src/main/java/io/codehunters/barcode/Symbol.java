package io.codehunters.barcode;

import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
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
final class Symbol {

  private final BitMatrix modules;
  private final int margin;
  private final boolean linear;

  private Symbol(BitMatrix modules, int margin, boolean linear) {
    this.modules = modules;
    this.margin = margin;
    this.linear = linear;
  }

  static Symbol encode(BarcodeRequest request) {
    BarcodeFormat format = request.format();
    try {
      BitMatrix modules =
          new MultiFormatWriter().encode(request.content(), format.zxing(), 0, 0, hints(request));
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

  private static Map<EncodeHintType, Object> hints(BarcodeRequest request) {
    Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
    hints.put(EncodeHintType.MARGIN, 0);
    hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
    if (request.format() == BarcodeFormat.QR_CODE) {
      hints.put(EncodeHintType.ERROR_CORRECTION, request.errorCorrection().zxing());
    }
    return hints;
  }

  /** {@code length} dark modules starting at {@code (x, y)}. */
  record Run(int x, int y, int length) {}
}
