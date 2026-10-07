package io.codehunters.jpa.specification;

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.metamodel.Attribute;

/** Resolves property paths, joining associations once per query with left joins. */
final class PropertyPaths {

  private PropertyPaths() {}

  static Path<?> resolve(From<?, ?> root, String propertyPath, CriteriaQuery<?> query) {
    var segments = propertyPath.split("\\.");
    Path<?> current = root;
    for (int i = 0; i < segments.length - 1; i++) {
      current = step(current, segments[i], query);
    }
    return current.get(segments[segments.length - 1]);
  }

  static Path<?> requireValueAttribute(Path<?> path) {
    if (isAssociation(path)) {
      throw new InvalidSearchException(
          "only IS_NULL and IS_NOT_NULL apply to an association; search one of its attributes");
    }
    return path;
  }

  private static Path<?> step(Path<?> current, String segment, CriteriaQuery<?> query) {
    if (current instanceof From<?, ?> from && isAssociation(from.get(segment))) {
      return join(from, segment, query);
    }
    return current.get(segment);
  }

  private static Join<?, ?> join(From<?, ?> from, String attribute, CriteriaQuery<?> query) {
    for (Join<?, ?> existing : from.getJoins()) {
      if (existing.getAttribute().getName().equals(attribute)
          && existing.getJoinType() == JoinType.LEFT) {
        return existing;
      }
    }
    Join<?, ?> join = from.join(attribute, JoinType.LEFT);
    if (join.getAttribute().isCollection() && query != null) {
      query.distinct(true);
    }
    return join;
  }

  private static boolean isAssociation(Path<?> path) {
    return path.getModel() instanceof Attribute<?, ?> attribute
        && (attribute.isAssociation() || attribute.isCollection());
  }
}
