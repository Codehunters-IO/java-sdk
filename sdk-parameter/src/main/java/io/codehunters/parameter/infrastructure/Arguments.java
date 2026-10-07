package io.codehunters.parameter.infrastructure;

import java.util.Collection;
import java.util.Objects;

/** Argument checks shared by the JDBC adapter and the cache. */
final class Arguments {

  private Arguments() {}

  /**
   * Rejects a collection holding a {@code null} element.
   *
   * @param values the collection to check; may itself be {@code null}
   * @param name the argument name, for the message
   * @throws IllegalArgumentException if any element is {@code null}
   */
  static void requireNoNullElements(Collection<?> values, String name) {
    if (values != null && values.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(name + " must not contain null elements");
    }
  }
}
