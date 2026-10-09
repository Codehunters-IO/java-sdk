package io.codehunters.barcode;

import io.codehunters.barcode.internal.render.PngWriter;
import io.codehunters.barcode.internal.render.SvgWriter;
import io.codehunters.barcode.internal.render.Symbol;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

/**
 * Renders barcodes as PNG or SVG. Thread-safe. Needs no display: rendering only uses in-memory
 * {@code java.awt} images, so it works with {@code java.awt.headless=true}.
 *
 * <p>Requested sizes above the {@link BarcodeLimits} are rejected before encoding; a PNG that would
 * come out larger (the symbol needs more pixels than requested) is rejected before drawing.
 */
public final class BarcodeRenderer {

  private final BarcodeLimits limits;

  /** A renderer with {@link BarcodeLimits#DEFAULT}. */
  public BarcodeRenderer() {
    this(BarcodeLimits.DEFAULT);
  }

  /**
   * A renderer with the given limits.
   *
   * @param limits the largest images it renders
   */
  public BarcodeRenderer(BarcodeLimits limits) {
    if (limits == null) {
      throw new IllegalArgumentException("limits must not be null");
    }
    this.limits = limits;
  }

  /**
   * Renders the request into {@code out}, which is flushed but not closed.
   *
   * @param request what to render
   * @param format the image format
   * @param out where to write
   * @throws BarcodeRejectedException with code {@value BarcodeRejectedException#CONTENT_REJECTED}
   *     if the request exceeds the limits or the content does not fit the symbol
   * @throws IllegalArgumentException if an argument is null
   * @throws UncheckedIOException if {@code out} fails
   */
  public void render(BarcodeRequest request, ImageFormat format, OutputStream out) {
    if (request == null || format == null || out == null) {
      throw new IllegalArgumentException("request, format and output must not be null");
    }
    requireWithinLimits(request.width(), request.height(), "the requested size");
    Symbol symbol = Symbol.encode(request);
    try {
      write(symbol, request, format, out);
    } catch (IOException e) {
      throw new UncheckedIOException("the barcode could not be written", e);
    }
  }

  /**
   * Renders the request as a PNG.
   *
   * @param request what to render
   * @return the PNG bytes
   */
  public byte[] toPng(BarcodeRequest request) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    render(request, ImageFormat.PNG, out);
    return out.toByteArray();
  }

  private void write(Symbol symbol, BarcodeRequest request, ImageFormat format, OutputStream out)
      throws IOException {
    if (format == ImageFormat.SVG) {
      SvgWriter.write(symbol, request.width(), request.height(), out);
      return;
    }
    PngWriter.Layout layout = PngWriter.layout(symbol, request.width(), request.height());
    requireWithinLimits(layout.width(), layout.height(), "the rendered barcode");
    PngWriter.write(symbol, layout, out);
  }

  private void requireWithinLimits(int width, int height, String what) {
    if (width > limits.maxWidth() || height > limits.maxHeight()) {
      throw BarcodeRejectedException.content(
          what
              + " exceeds the limit of "
              + limits.maxWidth()
              + " x "
              + limits.maxHeight()
              + " pixels");
    }
  }
}
