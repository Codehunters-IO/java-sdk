package io.codehunters.jpa.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.errors.ClientInputException;
import io.codehunters.jpa.fixture.Order;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpecificationBuilderTest {

  private final SpecificationBuilder<Order> builder =
      SpecificationBuilder.of(Order.class, Map.of("status", "status", "customer", "customer.name"));

  @Test
  void should_reject_an_attribute_outside_the_whitelist_without_repeating_it() {
    var criteria = SearchCriteria.of("passwordHash", SearchOperation.EQUAL, "x");

    assertThatThrownBy(() -> builder.where(criteria))
        .isInstanceOf(InvalidSearchException.class)
        .hasMessageNotContaining("passwordHash")
        .hasMessageContaining("customer, status");
  }

  @Test
  void should_reject_an_attribute_outside_the_whitelist_with_the_invalid_search_code() {
    var criteria = SearchCriteria.of("passwordHash", SearchOperation.EQUAL, "x");

    assertThatThrownBy(() -> builder.where(criteria))
        .isInstanceOf(ClientInputException.class)
        .hasFieldOrPropertyWithValue("code", "INVALID_SEARCH");
  }

  @Test
  void should_reject_a_bad_whitelist_as_a_programming_error_not_as_client_input() {
    assertThatThrownBy(() -> SpecificationBuilder.of(Order.class, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .isNotInstanceOf(ClientInputException.class);
  }

  @Test
  void should_list_the_names_clients_may_search_by() {
    assertThat(builder.searchable()).containsExactly("customer", "status");
  }

  @Test
  void should_reject_more_criteria_than_the_cap() {
    var criteria =
        Collections.nCopies(
            SpecificationBuilder.MAX_CRITERIA + 1,
            SearchCriteria.of("status", SearchOperation.EQUAL, "OPEN"));

    assertThatThrownBy(() -> builder.allOf(criteria)).isInstanceOf(InvalidSearchException.class);
    assertThatThrownBy(() -> builder.anyOf(criteria)).isInstanceOf(InvalidSearchException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "1st", "a..b", "a.", "name;drop", "na me"})
  void should_reject_a_whitelist_entry_that_is_not_a_property_path(String entry) {
    assertThatThrownBy(() -> SpecificationBuilder.of(Order.class, entry))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SpecificationBuilder.of(Order.class, Map.of("name", entry)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_a_missing_entity_type_or_conversion_service() {
    assertThatThrownBy(() -> SpecificationBuilder.of(null, "status"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> builder.withConversionService(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_an_empty_whitelist() {
    assertThatThrownBy(() -> SpecificationBuilder.of(Order.class, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_missing_attribute_names_with_an_illegal_argument() {
    assertThatThrownBy(() -> SpecificationBuilder.of(Order.class, (String[]) null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SpecificationBuilder.of(Order.class, "status", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
