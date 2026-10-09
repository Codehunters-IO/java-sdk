package io.codehunters.web.errors;

/**
 * A stable, documented error identifier. Applications implement it with an enum per bounded
 * context; the {@link #code()} is part of the API contract and must not change once released.
 *
 * <pre>{@code
 * enum OrderErrorCode implements ErrorCode {
 *   ORDER_NOT_FOUND(404), ORDER_ALREADY_PAID(409);
 *   ...
 * }
 * }</pre>
 */
public interface ErrorCode {

  /**
   * The stable identifier sent to clients in the {@code code} extension, e.g. {@code
   * ORDER_NOT_FOUND}.
   *
   * @return the code
   */
  String code();

  /**
   * The HTTP status the error maps to. Values outside 400–599 are answered with 500.
   *
   * @return the status code
   */
  int status();

  /**
   * The {@code MessageSource} key of the human-readable detail. Defaults to {@code error.<code>}.
   * Arguments of the {@link ApiException} fill the message placeholders.
   *
   * @return the message key
   */
  default String messageKey() {
    return "error." + code();
  }
}
