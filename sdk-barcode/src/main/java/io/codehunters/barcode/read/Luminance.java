package io.codehunters.barcode.read;

import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import java.awt.image.BufferedImage;

/**
 * The grey levels ZXing searches, built one row at a time: one byte per pixel, with no full ARGB
 * copy of the image.
 */
public final class Luminance {

  private Luminance() {}

  public static LuminanceSource of(BufferedImage image) {
    int width = image.getWidth();
    int height = image.getHeight();
    byte[] grey = new byte[width * height];
    int[] row = new int[width];
    for (int y = 0; y < height; y++) {
      image.getRGB(0, y, width, 1, row, 0, width);
      fill(grey, y * width, row);
    }
    return new PlanarYUVLuminanceSource(grey, width, height, 0, 0, width, height, false);
  }

  /** The weighting of ZXing's {@code RGBLuminanceSource}: (red + 2 green + blue) / 4. */
  private static void fill(byte[] grey, int offset, int[] row) {
    for (int x = 0; x < row.length; x++) {
      int rgb = row[x];
      int sum = ((rgb >> 16) & 0xFF) + 2 * ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
      grey[offset + x] = (byte) (sum / 4);
    }
  }
}
