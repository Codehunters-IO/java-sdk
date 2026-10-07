package io.codehunters.jpa.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SearchCriteriaTest {

  @ParameterizedTest(name = "{0} with {1} values")
  @CsvSource({
    "EQUAL,1", "NOT_EQUAL,1", "LIKE,1", "GT,1", "GTE,1", "LT,1", "LTE,1",
    "BETWEEN,2", "IN,1", "IN,3", "IS_NULL,0", "IS_NOT_NULL,0"
  })
  void should_accept_the_number_of_values_an_operation_takes(SearchOperation operation, int count) {
    var values = Collections.nCopies(count, (Object) "x");

    assertThat(new SearchCriteria("status", operation, values).values()).hasSize(count);
  }

  @ParameterizedTest(name = "{0} with {1} values")
  @CsvSource({
    "EQUAL,0", "EQUAL,2", "LIKE,2", "GT,0", "BETWEEN,1", "BETWEEN,3", "IN,0", "IS_NULL,1",
    "IS_NOT_NULL,1"
  })
  void should_reject_a_number_of_values_the_operation_does_not_take(
      SearchOperation operation, int count) {
    var values = Collections.nCopies(count, (Object) "x");

    assertThatThrownBy(() -> new SearchCriteria("status", operation, values))
        .isInstanceOf(InvalidSearchException.class);
  }

  @Test
  void should_reject_more_in_values_than_the_cap() {
    var values = Collections.nCopies(SearchCriteria.MAX_IN_VALUES + 1, (Object) "x");

    assertThatThrownBy(() -> new SearchCriteria("status", SearchOperation.IN, values))
        .isInstanceOf(InvalidSearchException.class);
  }

  @Test
  void should_reject_a_null_value_and_point_to_is_null_instead() {
    assertThatThrownBy(() -> SearchCriteria.of("status", SearchOperation.EQUAL, (Object) null))
        .isInstanceOf(InvalidSearchException.class)
        .hasMessageContaining("IS_NULL");
  }

  @Test
  void should_reject_a_missing_attribute_operation_or_values() {
    assertThatThrownBy(() -> new SearchCriteria(" ", SearchOperation.IS_NULL, List.of()))
        .isInstanceOf(InvalidSearchException.class);
    assertThatThrownBy(() -> new SearchCriteria("status", null, List.of()))
        .isInstanceOf(InvalidSearchException.class);
    assertThatThrownBy(() -> new SearchCriteria("status", SearchOperation.IS_NULL, null))
        .isInstanceOf(InvalidSearchException.class);
  }

  @Test
  void should_keep_its_own_copy_of_the_values() {
    var values = new ArrayList<Object>(List.of("PAID"));
    var criteria = new SearchCriteria("status", SearchOperation.IN, values);

    values.add("OPEN");

    assertThat(criteria.values()).containsExactly("PAID");
    assertThatThrownBy(() -> criteria.values().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void should_build_from_varargs() {
    assertThat(SearchCriteria.of("amount", SearchOperation.BETWEEN, 10, 20).values())
        .isEqualTo(Arrays.asList(10, 20));
  }
}
