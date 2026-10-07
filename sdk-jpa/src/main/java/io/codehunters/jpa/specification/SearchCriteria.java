package io.codehunters.jpa.specification;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One search condition, typically bound from a request: an API attribute name, an operation and its
 * values. Values may be text; they are converted to the attribute's type when the query is built.
 *
 * @param attribute the API attribute name, checked against the builder's whitelist
 * @param operation the operation
 * @param values the operation's values: none for null checks, two for {@code BETWEEN}, up to
 *     {@value #MAX_IN_VALUES} for {@code IN}, one otherwise; never {@code null} elements
 */
public record SearchCriteria(String attribute, SearchOperation operation, List<Object> values) {

  /** Most values an {@code IN} condition may list. */
  public static final int MAX_IN_VALUES = 1000;

  /** Validates the condition and copies the values. */
  public SearchCriteria {
    require(attribute != null && !attribute.isBlank(), "the search attribute must not be blank");
    require(operation != null && values != null, "the search operation and values are required");
    require(
        values.stream().allMatch(Objects::nonNull), "search values must not be null; use IS_NULL");
    require(operation.accepts(values.size()), operation + " takes " + operation.arity());
    values = List.copyOf(values);
  }

  /**
   * Creates a condition.
   *
   * @param attribute the API attribute name
   * @param operation the operation
   * @param values the operation's values
   * @return the condition
   * @throws InvalidSearchException if the condition is not valid
   */
  public static SearchCriteria of(String attribute, SearchOperation operation, Object... values) {
    return new SearchCriteria(attribute, operation, values == null ? null : Arrays.asList(values));
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new InvalidSearchException(message);
    }
  }
}
