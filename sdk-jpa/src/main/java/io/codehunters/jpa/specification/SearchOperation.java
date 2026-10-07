package io.codehunters.jpa.specification;

/** Search operations and the number of values each takes. */
public enum SearchOperation {
  /** Equal to one value. */
  EQUAL(1, 1),
  /** Not equal to one value; rows where the attribute is null do not match (SQL semantics). */
  NOT_EQUAL(1, 1),
  /** Text containing one value, ignoring case; {@code %} and {@code _} match themselves. */
  LIKE(1, 1),
  /** Between two values, both included. */
  BETWEEN(2, 2),
  /** Equal to one of up to {@value SearchCriteria#MAX_IN_VALUES} values. */
  IN(1, SearchCriteria.MAX_IN_VALUES),
  /** Greater than one value. */
  GT(1, 1),
  /** Greater than or equal to one value. */
  GTE(1, 1),
  /** Less than one value. */
  LT(1, 1),
  /** Less than or equal to one value. */
  LTE(1, 1),
  /** The attribute is null; takes no value. */
  IS_NULL(0, 0),
  /** The attribute is not null; takes no value. */
  IS_NOT_NULL(0, 0);

  private final int minValues;
  private final int maxValues;

  SearchOperation(int minValues, int maxValues) {
    this.minValues = minValues;
    this.maxValues = maxValues;
  }

  boolean accepts(int count) {
    return count >= minValues && count <= maxValues;
  }

  String arity() {
    return minValues == maxValues
        ? minValues + " value(s)"
        : minValues + " to " + maxValues + " values";
  }
}
