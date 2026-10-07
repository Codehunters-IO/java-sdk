package io.codehunters.jpa.specification;

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.Attribute;

/**
 * Resolves property paths. A to-one association is left-joined once per query and shared by the
 * conditions that cross it. The first collection on a path opens a subquery correlated with the
 * outer row, one per condition, so each condition matches its own element and the outer query never
 * needs {@code distinct}.
 */
final class PropertyPaths {

  private PropertyPaths() {}

  static ResolvedPath resolve(Root<?> root, String propertyPath, CriteriaQuery<?> query) {
    var segments = propertyPath.split("\\.");
    var walk = new Walk(root, query);
    for (int i = 0; i < segments.length - 1; i++) {
      walk.step(segments[i]);
    }
    return walk.end(segments[segments.length - 1]);
  }

  static Path<?> requireValueAttribute(Path<?> path) {
    if (isAssociation(path)) {
      throw new InvalidSearchException(
          "only IS_NULL and IS_NOT_NULL apply to an association; search one of its attributes");
    }
    return path;
  }

  private static boolean isAssociation(Path<?> path) {
    return path.getModel() instanceof Attribute<?, ?> attribute
        && (attribute.isAssociation() || attribute.isCollection());
  }

  private static boolean isCollection(Path<?> path) {
    return path.getModel() instanceof Attribute<?, ?> attribute && attribute.isCollection();
  }

  /** One walk along a path; opens at most one subquery. */
  private static final class Walk {

    private final CriteriaQuery<?> query;
    private Path<?> current;
    private Subquery<Integer> subquery;

    Walk(Root<?> root, CriteriaQuery<?> query) {
      this.current = root;
      this.query = query;
    }

    void step(String segment) {
      current =
          current instanceof From<?, ?> from && isAssociation(from.get(segment))
              ? join(from, segment)
              : current.get(segment);
    }

    ResolvedPath end(String segment) {
      if (current instanceof From<?, ?> from && opensSubquery(from, segment)) {
        var owner = subquery == null ? enter(from) : from;
        return new ResolvedPath(owner.join(segment, JoinType.INNER), subquery, true);
      }
      return new ResolvedPath(current.get(segment), subquery, false);
    }

    private boolean opensSubquery(From<?, ?> from, String attribute) {
      return query != null && isCollection(from.get(attribute));
    }

    private Join<?, ?> join(From<?, ?> from, String attribute) {
      if (subquery != null) {
        return from.join(attribute, JoinType.LEFT);
      }
      if (opensSubquery(from, attribute)) {
        return enter(from).join(attribute, JoinType.INNER);
      }
      return sharedLeftJoin(from, attribute);
    }

    private From<?, ?> enter(From<?, ?> from) {
      subquery = query.subquery(Integer.class);
      return from instanceof Root<?> root
          ? subquery.correlate(root)
          : subquery.correlate((Join<?, ?>) from);
    }

    private static Join<?, ?> sharedLeftJoin(From<?, ?> from, String attribute) {
      for (Join<?, ?> existing : from.getJoins()) {
        if (existing.getAttribute().getName().equals(attribute)
            && existing.getJoinType() == JoinType.LEFT) {
          return existing;
        }
      }
      return from.join(attribute, JoinType.LEFT);
    }
  }
}
