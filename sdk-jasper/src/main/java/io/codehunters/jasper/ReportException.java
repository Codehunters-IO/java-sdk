package io.codehunters.jasper;

import java.io.Serial;

/**
 * A report could not be loaded, filled or exported. The message names the template at most, never a
 * parameter value or a row; the cause, kept for the server log, is JasperReports' own exception and
 * may quote data.
 */
public class ReportException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what failed, without report data
   */
  public ReportException(String message) {
    super(message);
  }

  /**
   * Creates the exception.
   *
   * @param message what failed, without report data
   * @param cause the underlying failure
   */
  public ReportException(String message, Throwable cause) {
    super(message, cause);
  }
}
