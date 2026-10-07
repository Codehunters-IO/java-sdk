package io.codehunters.jpa.specification;

/**
 * A search request cannot be honoured: an attribute outside the whitelist, a wrong number of
 * values, a value that does not fit the attribute, or an operation the attribute does not support.
 * Messages never repeat the client's input, so they are safe to return as a 400 detail.
 *
 * <p>Raised while the query is built, Spring may wrap it in {@code
 * InvalidDataAccessApiUsageException}; look for it in the cause chain.
 */
public final class InvalidSearchException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what is wrong, without the client's input
   */
  public InvalidSearchException(String message) {
    super(message);
  }
}
