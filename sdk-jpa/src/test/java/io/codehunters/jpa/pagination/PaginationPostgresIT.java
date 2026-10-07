package io.codehunters.jpa.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.jpa.fixture.Customer;
import io.codehunters.jpa.fixture.CustomerRepository;
import io.codehunters.jpa.fixture.JpaFixtures;
import io.codehunters.jpa.fixture.Order;
import io.codehunters.jpa.fixture.OrderRepository;
import io.codehunters.jpa.fixture.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PaginationPostgresIT {

  private static final Instant PLACED = Instant.parse("2026-10-01T00:00:00Z");

  private final ApplicationContextRunner runner = JpaFixtures.postgres();

  private final SortWhitelist whitelist =
      SortWhitelist.of(Map.of("ref", "reference", "customer", "customer.name"));

  @Test
  void should_page_with_a_whitelisted_api_sort_and_map_the_rows() {
    runner.run(
        context -> {
          store(context, "Zoe");
          store(context, "Ann");
          store(context, "Max");

          var page =
              context
                  .getBean(OrderRepository.class)
                  .findAll(whitelist.apply(PageRequest.of(0, 2, Sort.by("ref"))));
          var response = PageResponse.from(page, Order::getReference);

          assertThat(response.content()).containsExactly("A-Ann", "A-Max");
          assertThat(response.totalElements()).isEqualTo(3);
          assertThat(response.totalPages()).isEqualTo(2);
          assertThat(response.sort())
              .containsExactly(
                  new PageResponse.SortOrder("reference", PageResponse.SortOrder.Direction.ASC));
        });
  }

  @Test
  void should_sort_by_a_property_of_an_associated_entity() {
    runner.run(
        context -> {
          store(context, "Zoe");
          store(context, "Ann");
          store(context, "Max");

          var page =
              context
                  .getBean(OrderRepository.class)
                  .findAll(whitelist.apply(PageRequest.of(0, 3, Sort.by("customer").descending())));

          assertThat(page.map(Order::getReference).getContent())
              .containsExactly("A-Zoe", "A-Max", "A-Ann");
        });
  }

  private static void store(AssertableApplicationContext context, String name) {
    var customer = context.getBean(CustomerRepository.class).save(new Customer(name));
    context
        .getBean(OrderRepository.class)
        .save(new Order("A-" + name, OrderStatus.OPEN, BigDecimal.TEN, PLACED, customer));
  }
}
