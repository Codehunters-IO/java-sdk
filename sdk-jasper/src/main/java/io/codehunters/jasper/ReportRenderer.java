package io.codehunters.jasper;

import java.io.OutputStream;

/** Fills a report and exports it. Implementations are thread-safe. */
public interface ReportRenderer {

  /**
   * Fills the report and streams the document into {@code out}, which is left open.
   *
   * @param request what to render
   * @param out where the document goes
   * @throws ReportException when the template cannot be loaded, or the report filled or exported
   */
  void render(ReportRequest request, OutputStream out);

  /**
   * Fills the report and returns the document, failing once it grows past the output limit.
   *
   * @param request what to render
   * @return the document
   * @throws ReportException as {@link #render}, or when the document exceeds the limit
   */
  byte[] toBytes(ReportRequest request);
}
