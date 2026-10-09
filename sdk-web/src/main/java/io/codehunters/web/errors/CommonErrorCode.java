package io.codehunters.web.errors;

/** Error codes the SDK itself answers with. */
public enum CommonErrorCode implements ErrorCode {

  /** The request failed bean validation; the {@code errors} extension lists each violation. */
  VALIDATION_FAILED(400),

  /** An exception nobody mapped; details stay in the server log, keyed by the trace id. */
  INTERNAL_ERROR(500);

  private final int status;

  CommonErrorCode(int status) {
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
