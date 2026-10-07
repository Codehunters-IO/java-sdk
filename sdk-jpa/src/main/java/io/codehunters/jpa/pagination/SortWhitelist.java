package io.codehunters.jpa.pagination;

import io.codehunters.jpa.internal.PropertyWhitelist;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * The sort properties clients may request, each mapped from an API name to an entity property path.
 * Anything else is rejected, so a client cannot sort by an internal column or make the query fail
 * on an unknown property. Immutable and thread-safe.
 *
 * <pre>{@code
 * SortWhitelist SORTS = SortWhitelist.of(Map.of("created", "createdAt", "customer", "customer.name"));
 * repository.findAll(SORTS.apply(pageable));
 * }</pre>
 *
 * <p>The page size cap is Spring Data's: {@code spring.data.web.pageable.max-page-size}.
 */
public final class SortWhitelist {

  private final NavigableMap<String, String> properties;

  private SortWhitelist(Map<String, String> properties) {
    this.properties = PropertyWhitelist.of(properties, "sort");
  }

  /**
   * Allows sorting by entity properties under their own names.
   *
   * @param properties entity property paths, such as {@code reference} or {@code customer.name}
   * @return the whitelist
   * @throws IllegalArgumentException if a property is not a property path
   */
  public static SortWhitelist of(String... properties) {
    return of(PropertyWhitelist.identity(properties));
  }

  /**
   * Allows sorting by API names mapped to entity property paths.
   *
   * @param apiToProperty API sort name to entity property path
   * @return the whitelist
   * @throws IllegalArgumentException if the map is empty, or a name or path is not a property path
   */
  public static SortWhitelist of(Map<String, String> apiToProperty) {
    return new SortWhitelist(apiToProperty);
  }

  /**
   * Returns the names clients may sort by.
   *
   * @return the API names, sorted
   */
  public Set<String> allowed() {
    return properties.navigableKeySet();
  }

  /**
   * Validates and translates the sort of a page request; page and size are kept.
   *
   * @param requested the request, typically bound from query parameters
   * @return the request sorted by entity properties
   * @throws IllegalArgumentException if a sort name is not allowed or appears twice
   */
  public Pageable apply(Pageable requested) {
    var sort = apply(requested.getSort());
    return requested.isPaged()
        ? PageRequest.of(requested.getPageNumber(), requested.getPageSize(), sort)
        : Pageable.unpaged(sort);
  }

  /**
   * Validates and translates a sort; direction, case and null handling are kept.
   *
   * @param requested the requested sort
   * @return the sort by entity properties
   * @throws IllegalArgumentException if a sort name is not allowed or appears twice
   */
  public Sort apply(Sort requested) {
    var seen = new HashSet<String>();
    return Sort.by(requested.stream().map(order -> translate(order, seen)).toList());
  }

  private Sort.Order translate(Sort.Order order, Set<String> seen) {
    var property = properties.get(order.getProperty());
    require(
        property != null, "sort property is not allowed; allowed: " + String.join(", ", allowed()));
    require(seen.add(property), "a sort property may appear only once");
    return order.withProperty(property);
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }
}
