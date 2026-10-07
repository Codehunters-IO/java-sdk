package io.codehunters.barcode;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Image fixtures for the barcode tests. */
final class Images {

  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
  private static final int WHITE = 255;

  private Images() {}

  static BufferedImage read(byte[] bytes) {
    try {
      return ImageIO.read(new ByteArrayInputStream(bytes));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static byte[] whitePng(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    int[] white = new int[width * height];
    Arrays.fill(white, WHITE);
    image.getRaster().setSamples(0, 0, width, height, 0, white);
    return png(image);
  }

  /**
   * A PNG made of the signature and an IHDR chunk announcing {@code width x height} pixels, with no
   * image data: what a decompression bomb looks like before its pixels are inflated.
   */
  static byte[] pngHeaderOnly(int width, int height) {
    ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height);
    ihdr.put((byte) 8).put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
    byte[] type = "IHDR".getBytes(StandardCharsets.US_ASCII);
    CRC32 crc = new CRC32();
    crc.update(type);
    crc.update(ihdr.array());
    ByteBuffer chunk = ByteBuffer.allocate(8 + 4 + 4 + 13 + 4).put(PNG_SIGNATURE).putInt(13);
    return chunk.put(type).put(ihdr.array()).putInt((int) crc.getValue()).array();
  }

  private static byte[] png(BufferedImage image) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
