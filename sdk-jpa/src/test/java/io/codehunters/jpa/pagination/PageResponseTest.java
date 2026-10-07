package io.codehunters.jpa.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.jpa.pagination.PageResponse.SortOrder;
import io.codehunters.jpa.pagination.PageResponse.SortOrder.Direction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PageResponseTest {

  private static final PageRequest SECOND_PAGE_OF_TWO =
      PageRequest.of(1, 2, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("reference")));

  @Test
  void should_copy_the_page_position_and_totals_when_built_from_a_page() {
    var page = new PageImpl<>(List.of("c", "d"), SECOND_PAGE_OF_TWO, 5);

    var response = PageResponse.from(page);

    assertThat(response.content()).containsExactly("c", "d");
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.size()).isEqualTo(2);
    assertThat(response.totalElements()).isEqualTo(5);
    assertThat(response.totalPages()).isEqualTo(3);
  }

  @Test
  void should_map_each_element_when_a_mapper_is_given() {
    var page = new PageImpl<>(List.of(1, 2), SECOND_PAGE_OF_TWO, 4);

    assertThat(PageResponse.from(page, n -> "#" + n).content()).containsExactly("#1", "#2");
  }

  @Test
  void should_carry_the_sort_orders_in_request_order() {
    var page = new PageImpl<>(List.of("c"), SECOND_PAGE_OF_TWO, 3);

    assertThat(PageResponse.from(page).sort())
        .containsExactly(
            new SortOrder("createdAt", Direction.DESC), new SortOrder("reference", Direction.ASC));
  }

  @Test
  void should_describe_an_unpaged_result_as_one_page_holding_everything() {
    var response = PageResponse.from(new PageImpl<>(List.of("a", "b", "c")));

    assertThat(response.page()).isZero();
    assertThat(response.size()).isEqualTo(3);
    assertThat(response.totalPages()).isEqualTo(1);
    assertThat(response.sort()).isEmpty();
  }

  @Test
  void should_not_let_callers_change_the_content() {
    var content = new ArrayList<>(List.of("a"));
    var response = new PageResponse<>(content, 0, 1, 1, 1, List.of());

    content.add("b");

    assertThat(response.content()).containsExactly("a");
    assertThatThrownBy(() -> response.content().add("c"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void should_reject_a_mapper_that_returns_null() {
    var page = new PageImpl<>(List.of(1), SECOND_PAGE_OF_TWO, 3);

    assertThatThrownBy(() -> PageResponse.from(page, n -> null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "page={0} size={1} total={2} pages={3}")
  @CsvSource({"-1,1,0,0", "0,-1,0,0", "0,1,-1,0", "0,1,0,-1"})
  void should_reject_negative_positions_or_totals(
      int page, int size, long totalElements, int totalPages) {
    assertThatThrownBy(
            () -> new PageResponse<>(List.of(), page, size, totalElements, totalPages, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_missing_content_or_sort() {
    assertThatThrownBy(() -> new PageResponse<>(null, 0, 0, 0, 0, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PageResponse<>(List.of(), 0, 0, 0, 0, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_a_sort_order_without_property_or_direction() {
    assertThatThrownBy(() -> new SortOrder(" ", Direction.ASC))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SortOrder("reference", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_null_elements_in_the_content() {
    assertThatThrownBy(() -> new PageResponse<>(Arrays.asList("a", null), 0, 2, 2, 1, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
