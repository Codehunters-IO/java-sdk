package io.codehunters.jpa.pagination;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

/**
 * One page of results with a stable JSON shape, independent of Spring Data's {@link Page}
 * serialization: {@code content}, {@code page} (zero-based), {@code size}, {@code totalElements},
 * {@code totalPages} and {@code sort}.
 *
 * @param content the elements of this page, never containing {@code null}
 * @param page the zero-based page number
 * @param size the requested page size; for an unpaged result, the number of elements
 * @param totalElements the number of elements across all pages
 * @param totalPages the number of pages
 * @param sort the orders the results were sorted by, in priority order
 * @param <T> the element type
 */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages, List<SortOrder> sort) {

  /** Validates the values and copies the lists. */
  public PageResponse {
    require(content != null && sort != null, "content and sort must not be null");
    require(content.stream().noneMatch(Objects::isNull), "content must not contain null");
    require(page >= 0 && size >= 0, "page and size must not be negative");
    require(totalElements >= 0 && totalPages >= 0, "totals must not be negative");
    content = List.copyOf(content);
    sort = List.copyOf(sort);
  }

  /**
   * Copies a Spring Data page.
   *
   * @param page the page to copy
   * @param <T> the element type
   * @return the response
   */
  public static <T> PageResponse<T> from(Page<T> page) {
    return from(page, Function.identity());
  }

  /**
   * Copies a Spring Data page, mapping each element, for example from an entity to a DTO.
   *
   * @param page the page to copy
   * @param mapper maps each element; must not return {@code null}
   * @param <E> the source element type
   * @param <T> the response element type
   * @return the response
   * @throws IllegalArgumentException if the mapper returns {@code null}
   */
  public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
    List<T> content = page.getContent().stream().<T>map(mapper).toList();
    return new PageResponse<>(
        content,
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getSort().stream().map(SortOrder::of).toList());
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }

  /**
   * One sort order.
   *
   * @param property the sorted property
   * @param direction ascending or descending
   */
  public record SortOrder(String property, Direction direction) {

    /** Validates the values. */
    public SortOrder {
      require(property != null && !property.isBlank(), "property must not be blank");
      require(direction != null, "direction must not be null");
    }

    static SortOrder of(Sort.Order order) {
      return new SortOrder(
          order.getProperty(), order.isAscending() ? Direction.ASC : Direction.DESC);
    }

    /** Sort direction. */
    public enum Direction {
      /** Ascending. */
      ASC,
      /** Descending. */
      DESC
    }
  }
}
