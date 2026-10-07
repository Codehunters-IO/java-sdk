package io.codehunters.jasper;

/** The document a report is exported to. */
public enum ReportFormat {
  /** A PDF document, through OpenPDF. */
  PDF("application/pdf", "pdf"),
  /** An Office Open XML workbook, one sheet per page by default. */
  XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
  /** Comma-separated values in UTF-8. */
  CSV("text/csv; charset=UTF-8", "csv");

  private final String mediaType;
  private final String extension;

  ReportFormat(String mediaType, String extension) {
    this.mediaType = mediaType;
    this.extension = extension;
  }

  /**
   * The media type to answer with, for a {@code Content-Type} header.
   *
   * @return the media type
   */
  public String mediaType() {
    return mediaType;
  }

  /**
   * The usual file extension, without the dot.
   *
   * @return the extension
   */
  public String extension() {
    return extension;
  }
}
