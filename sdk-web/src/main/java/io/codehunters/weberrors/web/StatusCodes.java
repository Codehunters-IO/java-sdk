package io.codehunters.weberrors.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** Names and reason phrases for status codes, including ones {@link HttpStatus} doesn't know. */
final class StatusCodes {

  private static final HttpStatusCode INTERNAL_ERROR = HttpStatus.INTERNAL_SERVER_ERROR;

  private StatusCodes() {}

  /**
   * The status an error code may answer with: 400–599, otherwise 500.
   *
   * @param status the declared status
   * @return a client or server error status
   */
  static HttpStatusCode errorStatus(int status) {
    return status >= 400 && status <= 599 ? HttpStatusCode.valueOf(status) : INTERNAL_ERROR;
  }

  /** The code for a status, e.g. {@code NOT_FOUND}; {@code HTTP_499} for unnamed ones. */
  static String code(HttpStatusCode status) {
    HttpStatus known = HttpStatus.resolve(status.value());
    return known != null ? known.name() : "HTTP_" + status.value();
  }

  /** The reason phrase, e.g. {@code Not Found}; {@code Error} for unnamed statuses. */
  static String reasonPhrase(HttpStatusCode status) {
    HttpStatus known = HttpStatus.resolve(status.value());
    return known != null ? known.getReasonPhrase() : "Error";
  }
}
