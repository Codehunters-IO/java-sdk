package io.codehunters.jpa.internal;

import java.util.function.Function;

/** Argument checks shared by the SDK's value types. Internal: not part of the SDK's API. */
public final class Checks {

  private Checks() {}

  /**
   * Throws the exception built from the message when the condition does not hold.
   *
   * @param condition what must hold
   * @param message the message of the exception
   * @param exception builds the exception from the message, such as {@code
   *     IllegalArgumentException::new}
   */
  public static void require(
      boolean condition, String message, Function<String, ? extends RuntimeException> exception) {
    if (!condition) {
      throw exception.apply(message);
    }
  }
}
