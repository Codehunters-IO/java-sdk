package io.codehunters.jpa.specification;

import static io.codehunters.jpa.specification.SearchOperation.BETWEEN;
import static io.codehunters.jpa.specification.SearchOperation.EQUAL;
import static io.codehunters.jpa.specification.SearchOperation.GT;
import static io.codehunters.jpa.specification.SearchOperation.GTE;
import static io.codehunters.jpa.specification.SearchOperation.IN;
import static io.codehunters.jpa.specification.SearchOperation.IS_NOT_NULL;
import static io.codehunters.jpa.specification.SearchOperation.IS_NULL;
import static io.codehunters.jpa.specification.SearchOperation.LIKE;
import static io.codehunters.jpa.specification.SearchOperation.LT;
import static io.codehunters.jpa.specification.SearchOperation.LTE;
import static io.codehunters.jpa.specification.SearchOperation.NOT_EQUAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.errors.ClientInputException;
import io.codehunters.jpa.fixture.Customer;
import io.codehunters.jpa.fixture.CustomerRepository;
import io.codehunters.jpa.fixture.JpaFixtures;
import io.codehunters.jpa.fixture.Order;
import io.codehunters.jpa.fixture.OrderRepository;
import io.codehunters.jpa.fixture.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.jpa.domain.Specification;

class SpecificationPostgresIT {

  private final ApplicationContextRunner runner = JpaFixtures.postgres();

  private final SpecificationBuilder<Order> search =
      SpecificationBuilder.of(
          Order.class,
          Map.of(
              "ref", "reference",
              "status", "status",
              "amount", "amount",
              "placed", "placedAt",
              "customerName", "customer.name",
              "customer", "customer"));

  @Test
  void should_match_an_enum_given_as_text() {
    assertFound(search.where(SearchCriteria.of("status", EQUAL, "PAID")), "A-Ann", "a_b");
  }

  @Test
  void should_exclude_a_value() {
    assertFound(
        search.where(SearchCriteria.of("status", NOT_EQUAL, "OPEN")), "A-Ann", "Promo 50%", "a_b");
  }

  @Test
  void should_find_text_containing_the_value_ignoring_case() {
    assertFound(search.where(SearchCriteria.of("ref", LIKE, "ann")), "A-Ann");
  }

  @Test
  void should_treat_percent_in_a_like_value_as_a_literal() {
    assertFound(search.where(SearchCriteria.of("ref", LIKE, "50%")), "Promo 50%");
  }

  @Test
  void should_treat_underscore_in_a_like_value_as_a_literal() {
    assertFound(search.where(SearchCriteria.of("ref", LIKE, "a_b")), "a_b");
  }

  @Test
  void should_include_both_ends_of_a_range_converted_from_text() {
    assertFound(
        search.where(SearchCriteria.of("amount", BETWEEN, "10", "20")), "A-Ann", "A-Max", "a_b");
  }

  @Test
  void should_match_any_of_several_values() {
    assertFound(
        search.where(SearchCriteria.of("status", IN, "PAID", "CANCELLED")),
        "A-Ann",
        "Promo 50%",
        "a_b");
  }

  @Test
  void should_compare_instants_given_as_iso_text() {
    var placed = "2026-10-03T00:00:00Z";

    assertFound(search.where(SearchCriteria.of("placed", GT, placed)), "Promo 500", "a_b", "axb");
    assertFound(
        search.where(SearchCriteria.of("placed", GTE, placed)),
        "Promo 50%",
        "Promo 500",
        "a_b",
        "axb");
    assertFound(search.where(SearchCriteria.of("placed", LT, placed)), "A-Ann", "A-Max");
    assertFound(
        search.where(SearchCriteria.of("placed", LTE, placed)), "A-Ann", "A-Max", "Promo 50%");
  }

  @Test
  void should_find_rows_with_and_without_an_association() {
    assertFound(search.where(SearchCriteria.of("customer", IS_NULL)), "Promo 500", "axb");
    assertFound(
        search.where(SearchCriteria.of("customer", IS_NOT_NULL)),
        "A-Ann",
        "A-Max",
        "Promo 50%",
        "a_b");
  }

  @Test
  void should_search_a_property_of_an_associated_entity() {
    assertFound(search.where(SearchCriteria.of("customerName", EQUAL, "Ann")), "A-Ann", "a_b");
  }

  @Test
  void should_require_every_criterion_with_all_of() {
    assertFound(
        search.allOf(
            List.of(
                SearchCriteria.of("customerName", EQUAL, "Ann"),
                SearchCriteria.of("amount", GT, "12"))),
        "a_b");
  }

  @Test
  void should_require_any_criterion_with_any_of() {
    assertFound(
        search.anyOf(
            List.of(
                SearchCriteria.of("status", EQUAL, "CANCELLED"),
                SearchCriteria.of("ref", EQUAL, "axb"))),
        "Promo 50%",
        "axb");
  }

  @Test
  void should_keep_rows_without_the_association_when_or_combining_a_nested_path() {
    assertFound(
        search.anyOf(
            List.of(
                SearchCriteria.of("customerName", EQUAL, "Max"),
                SearchCriteria.of("ref", EQUAL, "axb"))),
        "A-Max",
        "axb");
  }

  @Test
  void should_not_restrict_when_there_are_no_criteria() {
    assertFound(search.allOf(List.of()), "A-Ann", "A-Max", "Promo 50%", "Promo 500", "a_b", "axb");
  }

  @Test
  void should_reject_a_value_that_cannot_become_the_attribute_type_without_repeating_it() {
    runner.run(
        context -> {
          seed(context);
          var spec = search.where(SearchCriteria.of("amount", EQUAL, "ten-dollars"));

          assertThatThrownBy(() -> context.getBean(OrderRepository.class).findAll(spec))
              .hasRootCauseInstanceOf(InvalidSearchException.class)
              .rootCause()
              .hasMessageNotContaining("ten-dollars");
        });
  }

  @Test
  void should_not_name_the_internal_attribute_type_when_a_value_cannot_be_converted() {
    runner.run(
        context -> {
          seed(context);
          var spec = search.where(SearchCriteria.of("status", EQUAL, "NOT_A_STATUS"));

          assertThatThrownBy(() -> context.getBean(OrderRepository.class).findAll(spec))
              .rootCause()
              .hasMessageNotContaining("OrderStatus")
              .hasMessageNotContaining("NOT_A_STATUS");
        });
  }

  @Test
  void should_surface_a_conversion_failure_as_client_input_with_the_invalid_search_code() {
    runner.run(
        context -> {
          seed(context);
          var spec = search.where(SearchCriteria.of("amount", EQUAL, "ten-dollars"));

          assertThatThrownBy(() -> context.getBean(OrderRepository.class).findAll(spec))
              .rootCause()
              .isInstanceOf(ClientInputException.class)
              .hasFieldOrPropertyWithValue("code", "INVALID_SEARCH");
        });
  }

  @Test
  void should_reject_like_on_an_attribute_that_is_not_text() {
    runner.run(
        context -> {
          seed(context);
          var spec = search.where(SearchCriteria.of("amount", LIKE, "1"));

          assertThatThrownBy(() -> context.getBean(OrderRepository.class).findAll(spec))
              .hasRootCauseInstanceOf(InvalidSearchException.class);
        });
  }

  @Test
  void should_reject_an_order_comparison_on_an_attribute_that_cannot_be_ordered() {
    runner.run(
        context -> {
          seed(context);
          var spec = search.where(SearchCriteria.of("customer", GT, "x"));

          assertThatThrownBy(() -> context.getBean(OrderRepository.class).findAll(spec))
              .hasRootCauseInstanceOf(InvalidSearchException.class);
        });
  }

  @Test
  void should_reject_an_order_comparison_on_an_enum() {
    runner.run(
        context -> {
          seed(context);
          var spec = search.where(SearchCriteria.of("status", GT, "OPEN"));

          assertThatThrownBy(() -> context.getBean(OrderRepository.class).findAll(spec))
              .hasRootCauseInstanceOf(InvalidSearchException.class);
        });
  }

  @Test
  void should_find_the_same_rows_with_a_specification_restored_from_serialized_form()
      throws Exception {
    var restored = roundTrip(search.where(SearchCriteria.of("customerName", EQUAL, "Ann")));

    assertFound(restored, "A-Ann", "a_b");
  }

  @SuppressWarnings("unchecked")
  private static Specification<Order> roundTrip(Specification<Order> specification)
      throws Exception {
    var bytes = new java.io.ByteArrayOutputStream();
    try (var out = new java.io.ObjectOutputStream(bytes)) {
      out.writeObject(specification);
    }
    try (var in =
        new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
      return (Specification<Order>) in.readObject();
    }
  }

  private void assertFound(Specification<Order> specification, String... references) {
    runner.run(
        context -> {
          seed(context);

          var found =
              context.getBean(OrderRepository.class).findAll(specification).stream()
                  .map(Order::getReference)
                  .toList();

          assertThat(found).containsExactlyInAnyOrder(references);
        });
  }

  private static void seed(AssertableApplicationContext context) {
    var customers = context.getBean(CustomerRepository.class);
    var ann = customers.save(new Customer("Ann"));
    var max = customers.save(new Customer("Max"));
    var zoe = customers.save(new Customer("Zoe"));
    var orders = context.getBean(OrderRepository.class);
    orders.save(order("A-Ann", OrderStatus.PAID, "10.00", "2026-10-01T00:00:00Z", ann));
    orders.save(order("A-Max", OrderStatus.OPEN, "20.00", "2026-10-02T00:00:00Z", max));
    orders.save(order("Promo 50%", OrderStatus.CANCELLED, "30.00", "2026-10-03T00:00:00Z", zoe));
    orders.save(order("Promo 500", OrderStatus.OPEN, "5.00", "2026-10-04T00:00:00Z", null));
    orders.save(order("a_b", OrderStatus.PAID, "15.00", "2026-10-05T00:00:00Z", ann));
    orders.save(order("axb", OrderStatus.OPEN, "25.00", "2026-10-06T00:00:00Z", null));
  }

  private static Order order(
      String reference, OrderStatus status, String amount, String placedAt, Customer customer) {
    return new Order(reference, status, new BigDecimal(amount), Instant.parse(placedAt), customer);
  }
}
