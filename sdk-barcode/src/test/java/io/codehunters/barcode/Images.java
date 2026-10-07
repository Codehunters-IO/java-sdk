package io.codehunters.barcode;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Image fixtures for the barcode tests. */
final class Images {

  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
  private static final int WHITE = 255;
  private static final int BLACK_RGB = 0x000000;
  private static final int WHITE_RGB = 0xFFFFFF;

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
   * A black PNG with 16 bits per sample and alpha: 8 bytes per pixel once decoded, tiny on disk.
   */
  static byte[] rgba16Png(int width, int height) {
    ComponentColorModel model =
        new ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB),
            new int[] {16, 16, 16, 16},
            true,
            false,
            Transparency.TRANSLUCENT,
            DataBuffer.TYPE_USHORT);
    return png(
        new BufferedImage(model, model.createCompatibleWritableRaster(width, height), false, null));
  }

  /** A blank bilevel image written by the JDK's writer for {@code formatName} (tiff, wbmp...). */
  static byte[] encoded(String formatName, int width, int height) {
    return write(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY), formatName);
  }

  /** The PNG re-encoded as a grayscale {@code formatName} image (jpeg, gif, bmp...). */
  static byte[] reencoded(byte[] png, String formatName) {
    BufferedImage source = read(png);
    BufferedImage gray =
        new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D graphics = gray.createGraphics();
    graphics.drawImage(source, 0, 0, null);
    graphics.dispose();
    return write(gray, formatName);
  }

  /** A GIF with a screen descriptor and a trailer but no frame: its header cannot be read. */
  static byte[] gifWithoutFrames() {
    return new byte[] {'G', 'I', 'F', '8', '9', 'a', 10, 0, 10, 0, 0, 0, 0, 0x3B};
  }

  /**
   * A QR code as a third-party generator writes it: ISO-8859-1 bytes and no ECI segment, so the
   * reader has to guess the character set.
   */
  static byte[] latin1QrWithoutEci(String content, int size) {
    try {
      BitMatrix matrix =
          new QRCodeWriter()
              .encode(
                  content,
                  com.google.zxing.BarcodeFormat.QR_CODE,
                  size,
                  size,
                  Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.ISO_8859_1.name()));
      return png(toImage(matrix));
    } catch (WriterException e) {
      throw new IllegalStateException(e);
    }
  }

  private static BufferedImage toImage(BitMatrix matrix) {
    BufferedImage image =
        new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
    for (int y = 0; y < matrix.getHeight(); y++) {
      for (int x = 0; x < matrix.getWidth(); x++) {
        image.setRGB(x, y, matrix.get(x, y) ? BLACK_RGB : WHITE_RGB);
      }
    }
    return image;
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
    return write(image, "png");
  }

  private static byte[] write(BufferedImage image, String formatName) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      if (!ImageIO.write(image, formatName, out)) {
        throw new IllegalStateException("no ImageIO writer for " + formatName);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
