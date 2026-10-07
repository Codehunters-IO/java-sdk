package io.codehunters.jpa.specification;

import static io.codehunters.jpa.specification.SearchOperation.EQUAL;
import static io.codehunters.jpa.specification.SearchOperation.IS_NOT_NULL;
import static io.codehunters.jpa.specification.SearchOperation.IS_NULL;
import static io.codehunters.jpa.specification.SearchOperation.LIKE;
import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.jpa.fixture.Customer;
import io.codehunters.jpa.fixture.CustomerRepository;
import io.codehunters.jpa.fixture.JpaFixtures;
import io.codehunters.jpa.fixture.Order;
import io.codehunters.jpa.fixture.OrderLine;
import io.codehunters.jpa.fixture.OrderLineRepository;
import io.codehunters.jpa.fixture.OrderRepository;
import io.codehunters.jpa.fixture.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

class CollectionPathsPostgresIT {

  private static final Instant PLACED = Instant.parse("2026-10-01T00:00:00Z");

  private final ApplicationContextRunner runner = JpaFixtures.postgres();

  private final SpecificationBuilder<Order> search =
      SpecificationBuilder.of(Order.class, Map.of("sku", "lines.sku", "lines", "lines"));

  @Test
  void should_match_a_row_whose_different_elements_meet_each_condition() {
    assertFound(
        search.allOf(
            List.of(SearchCriteria.of("sku", EQUAL, "A"), SearchCriteria.of("sku", EQUAL, "B"))),
        "Ann-1");
  }

  @Test
  void should_sort_by_an_association_while_searching_a_collection() {
    runner.run(
        context -> {
          seed(context);

          var page =
              context
                  .getBean(OrderRepository.class)
                  .findAll(
                      search.where(SearchCriteria.of("sku", LIKE, "a")),
                      PageRequest.of(0, 1, Sort.by(Sort.Order.desc("customer.name"))));

          assertThat(page.getTotalElements()).isEqualTo(2);
          assertThat(page.getContent()).extracting(Order::getReference).containsExactly("Max-1");
        });
  }

  @Test
  void should_find_rows_with_and_without_elements_in_a_collection() {
    assertFound(search.where(SearchCriteria.of("lines", IS_NOT_NULL)), "Ann-1", "Max-1");
    assertFound(search.where(SearchCriteria.of("lines", IS_NULL)), "Zoe-1");
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
    var people = context.getBean(CustomerRepository.class);
    var orders = context.getBean(OrderRepository.class);
    var lines = context.getBean(OrderLineRepository.class);
    var ann = orders.save(order("Ann-1", people.save(new Customer("Ann"))));
    var max = orders.save(order("Max-1", people.save(new Customer("Max"))));
    orders.save(order("Zoe-1", people.save(new Customer("Zoe"))));
    lines.save(new OrderLine("A", ann));
    lines.save(new OrderLine("B", ann));
    lines.save(new OrderLine("A", max));
  }

  private static Order order(String reference, Customer customer) {
    return new Order(reference, OrderStatus.OPEN, BigDecimal.ONE, PLACED, customer);
  }
}
