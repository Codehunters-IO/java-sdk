package io.codehunters.jpa.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Locale;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ClassUtils;

/** The specification of one whitelisted condition. */
final class CriteriaSpecification<T> implements Specification<T> {

  private static final long serialVersionUID = 1L;
  private static final char ESCAPE = '\\';

  private final String propertyPath;
  private final SearchCriteria criteria;
  private final transient ConversionService conversions;

  CriteriaSpecification(
      String propertyPath, SearchCriteria criteria, ConversionService conversions) {
    this.propertyPath = propertyPath;
    this.criteria = criteria;
    this.conversions = conversions;
  }

  @Override
  public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
    var path = PropertyPaths.resolve(root, propertyPath, query);
    return switch (criteria.operation()) {
      case IS_NULL -> cb.isNull(path);
      case IS_NOT_NULL -> cb.isNotNull(path);
      default -> valuePredicate(PropertyPaths.requireValueAttribute(path), cb);
    };
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Predicate valuePredicate(Path<?> path, CriteriaBuilder cb) {
    return switch (criteria.operation()) {
      case EQUAL -> cb.equal(path, value(path, 0));
      case NOT_EQUAL -> cb.notEqual(path, value(path, 0));
      case LIKE -> like(path, cb);
      case IN -> path.in(values(path));
      case BETWEEN -> cb.between(ordered(path), ordered(path, 0), ordered(path, 1));
      case GT -> cb.greaterThan(ordered(path), ordered(path, 0));
      case GTE -> cb.greaterThanOrEqualTo(ordered(path), ordered(path, 0));
      case LT -> cb.lessThan(ordered(path), ordered(path, 0));
      default -> cb.lessThanOrEqualTo(ordered(path), ordered(path, 0));
    };
  }

  @SuppressWarnings("unchecked")
  private Predicate like(Path<?> path, CriteriaBuilder cb) {
    if (path.getJavaType() != String.class) {
      throw new InvalidSearchException("LIKE needs a text attribute");
    }
    var text = String.valueOf(criteria.values().get(0)).toLowerCase(Locale.ROOT);
    return cb.like(cb.lower((Expression<String>) path), "%" + escape(text) + "%", ESCAPE);
  }

  private Object value(Path<?> path, int index) {
    return convert(criteria.values().get(index), targetType(path));
  }

  private List<Object> values(Path<?> path) {
    var target = targetType(path);
    return criteria.values().stream().map(value -> convert(value, target)).toList();
  }

  @SuppressWarnings("rawtypes")
  private Comparable ordered(Path<?> path, int index) {
    ordered(path);
    return (Comparable) value(path, index);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Expression<Comparable> ordered(Path<?> path) {
    if (!Comparable.class.isAssignableFrom(targetType(path))) {
      throw new InvalidSearchException(
          "this operation needs an attribute whose values can be ordered");
    }
    return (Expression<Comparable>) path;
  }

  private Object convert(Object value, Class<?> target) {
    if (target.isInstance(value)) {
      return value;
    }
    try {
      var converted = conversions.convert(value, target);
      if (converted == null) {
        throw cannotConvert(target);
      }
      return converted;
    } catch (ConversionException | IllegalArgumentException e) {
      throw cannotConvert(target);
    }
  }

  private static InvalidSearchException cannotConvert(Class<?> target) {
    return new InvalidSearchException(
        "a search value cannot be converted to " + target.getSimpleName());
  }

  private static Class<?> targetType(Path<?> path) {
    return ClassUtils.resolvePrimitiveIfNecessary(path.getJavaType());
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
