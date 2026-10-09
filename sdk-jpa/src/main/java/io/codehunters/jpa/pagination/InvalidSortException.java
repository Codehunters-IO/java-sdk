package io.codehunters.jpa.pagination;

import io.codehunters.commons.error.ClientInputException;
import java.io.Serial;

/**
 * A requested sort cannot be honoured: a property outside the {@link SortWhitelist}, or the same
 * property twice. Its code is {@value #CODE}. Messages never repeat the client's input, so they are
 * safe to return as a 400 detail; sdk-web does so out of the box.
 *
 * <p>Still an {@link IllegalArgumentException}, which {@code SortWhitelist} threw before.
 */
public final class InvalidSortException extends ClientInputException {

  /** The stable error code. */
  public static final String CODE = "INVALID_SORT";

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what is wrong, without the client's input
   */
  public InvalidSortException(String message) {
    super(CODE, message);
  }
}
