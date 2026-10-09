package io.codehunters.barcode.render;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Writes a {@link Symbol} as SVG in module units: one {@code rect} per run of dark modules, scaled
 * to the requested size by the viewer. 2D symbols keep their proportions; linear bars stretch to
 * the height.
 */
public final class SvgWriter {

  private SvgWriter() {}

  public static void write(Symbol symbol, int width, int height, OutputStream out)
      throws IOException {
    Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
    writer.write(header(symbol, width, height));
    for (Symbol.Run run : symbol.runs()) {
      writer.write(
          "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"1\"/>"
              .formatted(run.x(), run.y(), run.length()));
    }
    writer.write("</g></svg>");
    writer.flush();
  }

  private static String header(Symbol symbol, int width, int height) {
    String aspect = symbol.isLinear() ? "none" : "xMidYMid meet";
    return ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\""
            + " viewBox=\"0 0 %d %d\" preserveAspectRatio=\"%s\" shape-rendering=\"crispEdges\">"
            + "<rect width=\"%d\" height=\"%d\" fill=\"#ffffff\"/><g fill=\"#000000\">")
        .formatted(
            width,
            height,
            symbol.columns(),
            symbol.rows(),
            aspect,
            symbol.columns(),
            symbol.rows());
  }
}
