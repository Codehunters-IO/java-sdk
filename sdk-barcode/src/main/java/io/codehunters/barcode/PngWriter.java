package io.codehunters.barcode;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Draws a {@link Symbol} into a black and white image: whole pixels per module, centred, at least
 * the requested size. ImageIO's file cache is bypassed, so nothing is written to disk.
 */
final class PngWriter {

  private PngWriter() {}

  static Layout layout(Symbol symbol, int width, int height) {
    int scale = Math.max(1, Math.min(width / symbol.columns(), height / symbol.rows()));
    if (symbol.isLinear()) {
      scale = Math.max(1, width / symbol.columns());
    }
    int imageWidth = Math.max(width, symbol.columns() * scale);
    int imageHeight = symbol.isLinear() ? height : Math.max(height, symbol.rows() * scale);
    return new Layout(imageWidth, imageHeight, scale);
  }

  static void write(Symbol symbol, Layout layout, OutputStream out) throws IOException {
    BufferedImage image = draw(symbol, layout);
    ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
    try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
      writer.setOutput(stream);
      writer.write(image);
    } finally {
      writer.dispose();
    }
  }

  private static BufferedImage draw(Symbol symbol, Layout layout) {
    BufferedImage image =
        new BufferedImage(layout.width(), layout.height(), BufferedImage.TYPE_BYTE_BINARY);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, layout.width(), layout.height());
      graphics.setColor(Color.BLACK);
      drawRuns(graphics, symbol, layout);
    } finally {
      graphics.dispose();
    }
    return image;
  }

  private static void drawRuns(Graphics2D graphics, Symbol symbol, Layout layout) {
    int scale = layout.scale();
    int left = (layout.width() - symbol.columns() * scale) / 2;
    int top = symbol.isLinear() ? 0 : (layout.height() - symbol.rows() * scale) / 2;
    int runHeight = symbol.isLinear() ? layout.height() : scale;
    for (Symbol.Run run : symbol.runs()) {
      graphics.fillRect(
          left + run.x() * scale, top + run.y() * scale, run.length() * scale, runHeight);
    }
  }

  /** Image size and pixels per module. */
  record Layout(int width, int height, int scale) {}
}
