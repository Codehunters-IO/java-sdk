package io.codehunters.jpa.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;

/**
 * Where a property path leads.
 *
 * @param path the attribute to test
 * @param subquery the correlated subquery the attribute lives in when the path crosses a
 *     collection, otherwise {@code null}
 * @param collection whether the path ends on the collection itself
 */
record ResolvedPath(Path<?> path, Subquery<Integer> subquery, boolean collection) {

  /** Applies a condition where the attribute lives: directly, or as "some element matches". */
  Predicate within(Predicate condition, CriteriaBuilder cb) {
    return subquery == null
        ? condition
        : cb.exists(subquery.select(cb.literal(1)).where(condition));
  }
}
