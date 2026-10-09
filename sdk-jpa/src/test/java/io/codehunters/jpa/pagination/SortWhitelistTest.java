package io.codehunters.jpa.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.commons.error.ClientInputException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.NullHandling;

class SortWhitelistTest {

  private final SortWhitelist whitelist =
      SortWhitelist.of(Map.of("created", "createdAt", "customer", "customer.name"));

  @Test
  void should_keep_an_allowed_property_and_its_direction() {
    assertThat(SortWhitelist.of("reference").apply(Sort.by(Sort.Order.desc("reference"))))
        .containsExactly(Sort.Order.desc("reference"));
  }

  @Test
  void should_translate_the_api_name_into_the_entity_property() {
    assertThat(whitelist.apply(Sort.by(Sort.Order.asc("customer"))))
        .containsExactly(Sort.Order.asc("customer.name"));
  }

  @Test
  void should_keep_ignore_case_and_null_handling() {
    var requested = new Sort.Order(Sort.Direction.ASC, "created", NullHandling.NULLS_LAST);

    assertThat(whitelist.apply(Sort.by(requested.ignoreCase())))
        .containsExactly(
            new Sort.Order(Sort.Direction.ASC, "createdAt", NullHandling.NULLS_LAST).ignoreCase());
  }

  @Test
  void should_reject_a_property_outside_the_whitelist_without_repeating_it() {
    assertThatThrownBy(() -> whitelist.apply(Sort.by("passwordHash")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining("passwordHash")
        .hasMessageContaining("created, customer");
  }

  @Test
  void should_reject_a_property_outside_the_whitelist_with_the_invalid_sort_code() {
    assertThatThrownBy(() -> whitelist.apply(Sort.by("passwordHash")))
        .isInstanceOf(InvalidSortException.class)
        .isInstanceOf(ClientInputException.class)
        .hasFieldOrPropertyWithValue("code", "INVALID_SORT");
  }

  @Test
  void should_reject_the_same_property_twice_with_the_invalid_sort_code() {
    assertThatThrownBy(() -> whitelist.apply(Sort.by("created", "created")))
        .isInstanceOf(InvalidSortException.class)
        .hasFieldOrPropertyWithValue("code", "INVALID_SORT");
  }

  @Test
  void should_reject_a_bad_whitelist_as_a_programming_error_not_as_client_input() {
    assertThatThrownBy(() -> SortWhitelist.of(Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .isNotInstanceOf(ClientInputException.class);
  }

  @Test
  void should_reject_the_same_property_twice() {
    assertThatThrownBy(() -> whitelist.apply(Sort.by("created", "created")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_keep_page_and_size_when_applied_to_a_pageable() {
    var applied = whitelist.apply(PageRequest.of(3, 20, Sort.by("customer")));

    assertThat(applied.getPageNumber()).isEqualTo(3);
    assertThat(applied.getPageSize()).isEqualTo(20);
    assertThat(applied.getSort()).containsExactly(Sort.Order.asc("customer.name"));
  }

  @Test
  void should_keep_an_unpaged_request_unpaged() {
    var applied = whitelist.apply(Pageable.unpaged(Sort.by("created")));

    assertThat(applied.isUnpaged()).isTrue();
    assertThat(applied.getSort()).containsExactly(Sort.Order.asc("createdAt"));
  }

  @Test
  void should_leave_an_unsorted_request_unsorted() {
    assertThat(whitelist.apply(Sort.unsorted()).isUnsorted()).isTrue();
  }

  @Test
  void should_list_the_names_clients_may_sort_by() {
    assertThat(whitelist.allowed()).containsExactly("created", "customer");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "1st", "a..b", "a.", "name;drop", "na me"})
  void should_reject_a_whitelist_entry_that_is_not_a_property_path(String entry) {
    assertThatThrownBy(() -> SortWhitelist.of(entry)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SortWhitelist.of(Map.of("name", entry)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_an_empty_whitelist() {
    assertThatThrownBy(() -> SortWhitelist.of(Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_a_missing_whitelist_or_a_missing_property() {
    var missingProperty = new java.util.HashMap<String, String>();
    missingProperty.put("created", null);

    assertThatThrownBy(() -> SortWhitelist.of((Map<String, String>) null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SortWhitelist.of(missingProperty))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_missing_property_names_with_an_illegal_argument() {
    assertThatThrownBy(() -> SortWhitelist.of((String[]) null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SortWhitelist.of("created", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_translate_entity_properties_back_into_api_names() {
    var sorts = SortWhitelist.of(Map.of("created", "createdAt", "customer", "customer.name"));

    var api =
        sorts.toApi(Sort.by(Sort.Order.desc("customer.name"), Sort.Order.asc("internalRank")));

    assertThat(api).containsExactly(Sort.Order.desc("customer"));
  }
}
