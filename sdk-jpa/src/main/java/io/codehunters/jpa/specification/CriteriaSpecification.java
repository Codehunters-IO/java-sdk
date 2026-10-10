package io.codehunters.jpa.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ClassUtils;

/**
 * The specification of one whitelisted condition. After deserialization it converts values with
 * Spring Boot's shared {@link ApplicationConversionService}: a custom conversion service is not
 * serialized.
 *
 * @param <T> the entity type
 */
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
    var target = PropertyPaths.resolve(root, propertyPath, query);
    if (target.collection()) {
      return collectionPredicate(target, cb);
    }
    return target.within(condition(target.path(), cb), cb);
  }

  private Predicate condition(Path<?> path, CriteriaBuilder cb) {
    return switch (criteria.operation()) {
      case IS_NULL -> cb.isNull(path);
      case IS_NOT_NULL -> cb.isNotNull(path);
      default -> valuePredicate(PropertyPaths.requireValueAttribute(path), cb);
    };
  }

  private Predicate collectionPredicate(ResolvedPath target, CriteriaBuilder cb) {
    return switch (criteria.operation()) {
      case IS_NOT_NULL -> target.within(cb.conjunction(), cb);
      case IS_NULL -> cb.not(target.within(cb.conjunction(), cb));
      default -> valuePredicate(PropertyPaths.requireValueAttribute(target.path()), cb);
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
      case LTE -> cb.lessThanOrEqualTo(ordered(path), ordered(path, 0));
      case IS_NULL, IS_NOT_NULL ->
          throw new IllegalStateException("null checks are built without a value");
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
    var type = targetType(path);
    if (type.isEnum() || !Comparable.class.isAssignableFrom(type)) {
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
      var converted = conversionService().convert(value, target);
      if (converted == null) {
        throw cannotConvert();
      }
      return converted;
    } catch (ConversionException | IllegalArgumentException e) {
      throw cannotConvert();
    }
  }

  private ConversionService conversionService() {
    return conversions != null ? conversions : ApplicationConversionService.getSharedInstance();
  }

  /**
   * The message names neither the value nor the attribute's Java type, which can be an internal
   * name (an enum such as {@code InternalRiskTier}) the client never sees.
   */
  private static InvalidSearchException cannotConvert() {
    return new InvalidSearchException("a search value has the wrong format for this field");
  }

  private static Class<?> targetType(Path<?> path) {
    return ClassUtils.resolvePrimitiveIfNecessary(path.getJavaType());
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
