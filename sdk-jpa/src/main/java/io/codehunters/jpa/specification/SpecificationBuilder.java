package io.codehunters.jpa.specification;

import io.codehunters.jpa.internal.PropertyWhitelist;
import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds Spring Data JPA {@link Specification}s from {@link SearchCriteria}, only over whitelisted
 * attributes. API names map to entity property paths; a path through an association, such as {@code
 * customer.name}, is reached with a left join that conditions on the same association share. Values
 * are converted to the attribute's type with a {@link ConversionService}. Immutable and
 * thread-safe: build one per entity and reuse it.
 *
 * <pre>{@code
 * SpecificationBuilder<Order> SEARCH =
 *     SpecificationBuilder.of(Order.class, Map.of("status", "status", "customer", "customer.name"));
 * repository.findAll(SEARCH.allOf(criteria), pageable);
 * }</pre>
 *
 * @param <T> the entity type
 */
public final class SpecificationBuilder<T> {

  /** Most criteria one {@link #allOf} or {@link #anyOf} call accepts. */
  public static final int MAX_CRITERIA = 32;

  private final NavigableMap<String, String> attributes;
  private final ConversionService conversions;

  private SpecificationBuilder(
      NavigableMap<String, String> attributes, ConversionService conversions) {
    this.attributes = attributes;
    this.conversions = conversions;
  }

  /**
   * Allows searching entity attributes under their own names.
   *
   * @param type the entity class
   * @param attributes entity property paths, such as {@code status} or {@code customer.name}
   * @param <T> the entity type
   * @return the builder, converting with Spring Boot's shared {@link ApplicationConversionService}
   * @throws IllegalArgumentException if {@code type} is null or an attribute is not a property path
   */
  public static <T> SpecificationBuilder<T> of(Class<T> type, String... attributes) {
    return of(type, PropertyWhitelist.identity(attributes));
  }

  /**
   * Allows searching by API names mapped to entity property paths.
   *
   * @param type the entity class
   * @param apiToPath API attribute name to entity property path
   * @param <T> the entity type
   * @return the builder, converting with Spring Boot's shared {@link ApplicationConversionService}
   * @throws IllegalArgumentException if {@code type} is null, the map is empty, or an entry is not
   *     a property path
   */
  public static <T> SpecificationBuilder<T> of(Class<T> type, Map<String, String> apiToPath) {
    if (type == null) {
      throw new IllegalArgumentException("the entity type must not be null");
    }
    return new SpecificationBuilder<>(
        PropertyWhitelist.of(apiToPath, "search"),
        ApplicationConversionService.getSharedInstance());
  }

  /**
   * Returns a builder converting values with the given service, for example the application's.
   *
   * @param conversionService the conversion service
   * @return a new builder with the same whitelist
   * @throws IllegalArgumentException if {@code conversionService} is null
   */
  public SpecificationBuilder<T> withConversionService(ConversionService conversionService) {
    if (conversionService == null) {
      throw new IllegalArgumentException("the conversion service must not be null");
    }
    return new SpecificationBuilder<>(attributes, conversionService);
  }

  /**
   * Returns the names clients may search by.
   *
   * @return the API names, sorted
   */
  public Set<String> searchable() {
    return attributes.navigableKeySet();
  }

  /**
   * Builds the specification of one condition.
   *
   * @param criteria the condition
   * @return the specification
   * @throws InvalidSearchException if the attribute is not whitelisted
   */
  public Specification<T> where(SearchCriteria criteria) {
    var path = attributes.get(criteria.attribute());
    if (path == null) {
      throw new InvalidSearchException(
          "search attribute is not allowed; allowed: " + String.join(", ", searchable()));
    }
    return new CriteriaSpecification<>(path, criteria, conversions);
  }

  /**
   * Builds a specification all conditions must meet; none means no restriction.
   *
   * @param criteria the conditions
   * @return the specification
   * @throws InvalidSearchException if there are more than {@value #MAX_CRITERIA} conditions or an
   *     attribute is not whitelisted
   */
  public Specification<T> allOf(Collection<SearchCriteria> criteria) {
    return criteria.isEmpty()
        ? Specification.unrestricted()
        : Specification.allOf(specifications(criteria));
  }

  /**
   * Builds a specification any condition may meet; none means no restriction.
   *
   * @param criteria the conditions
   * @return the specification
   * @throws InvalidSearchException if there are more than {@value #MAX_CRITERIA} conditions or an
   *     attribute is not whitelisted
   */
  public Specification<T> anyOf(Collection<SearchCriteria> criteria) {
    return criteria.isEmpty()
        ? Specification.unrestricted()
        : Specification.anyOf(specifications(criteria));
  }

  private java.util.List<Specification<T>> specifications(Collection<SearchCriteria> criteria) {
    if (criteria.size() > MAX_CRITERIA) {
      throw new InvalidSearchException("at most " + MAX_CRITERIA + " search criteria are allowed");
    }
    return criteria.stream().map(this::where).toList();
  }
}
