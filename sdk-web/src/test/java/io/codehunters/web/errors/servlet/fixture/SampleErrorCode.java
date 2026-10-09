package io.codehunters.web.errors.servlet.fixture;

import io.codehunters.web.errors.ErrorCode;

/** Error codes of the sample application used by the web tests. */
public enum SampleErrorCode implements ErrorCode {
  ORDER_NOT_FOUND(404),
  ORDER_ALREADY_PAID(409),
  OUT_OF_RANGE_STATUS(200),
  UNKNOWN_STATUS(799),
  SERVER_SIDE_FAILURE(503),
  TEAPOT_ORDER(418);

  private final int status;

  SampleErrorCode(int status) {
    this.status = status;
  }

  @Override
  public String code() {
    return name();
  }

  @Override
  public int status() {
    return status;
  }
}
