package io.codehunters.jpa.specification;

import io.codehunters.commons.error.ClientInputException;
import java.io.Serial;

/**
 * A search request cannot be honoured: an attribute outside the whitelist, a wrong number of
 * values, a value that does not fit the attribute, or an operation the attribute does not support.
 * Its code is {@value #CODE}. Messages never repeat the client's input, so they are safe to return
 * as a 400 detail; sdk-web does so out of the box.
 *
 * <p>Raised while the query is built, Spring may wrap it in {@code
 * InvalidDataAccessApiUsageException}; look for it in the cause chain. sdk-web does.
 *
 * <p>Still an {@link IllegalArgumentException}, as before it became a {@link ClientInputException}.
 */
public final class InvalidSearchException extends ClientInputException {

  /** The stable error code. */
  public static final String CODE = "INVALID_SEARCH";

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what is wrong, without the client's input
   */
  public InvalidSearchException(String message) {
    super(CODE, message);
  }
}
