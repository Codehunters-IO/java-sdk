package io.codehunters.jpa.specification;

import static io.codehunters.jpa.specification.SearchOperation.EQUAL;
import static io.codehunters.jpa.specification.SearchOperation.GT;
import static io.codehunters.jpa.specification.SearchOperation.LIKE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.jpa.fixture.Contact;
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
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class NestedPathsPostgresIT {

  private static final Instant PLACED = Instant.parse("2026-10-01T00:00:00Z");

  private final ApplicationContextRunner runner = JpaFixtures.postgres();

  private final SpecificationBuilder<Customer> customers =
      SpecificationBuilder.of(
          Customer.class,
          Map.of("orderRef", "orders.reference", "email", "contact.email", "name", "name"));

  @Test
  void should_count_each_customer_once_when_several_orders_match() {
    runner.run(
        context -> {
          seed(context);

          var page =
              context
                  .getBean(CustomerRepository.class)
                  .findAll(
                      customers.where(SearchCriteria.of("orderRef", LIKE, "a")),
                      PageRequest.of(0, 1, Sort.by("name")));

          assertThat(page.getTotalElements()).isEqualTo(2);
          assertThat(page.getContent()).extracting(Customer::getName).containsExactly("Ann");
        });
  }

  @Test
  void should_search_inside_an_embedded_value() {
    runner.run(
        context -> {
          seed(context);

          var found =
              context
                  .getBean(CustomerRepository.class)
                  .findAll(customers.where(SearchCriteria.of("email", EQUAL, "max@example.com")));

          assertThat(found).extracting(Customer::getName).containsExactly("Max");
        });
  }

  @Test
  void should_join_an_association_once_for_several_conditions_on_it() {
    runner.run(
        context -> {
          seed(context);

          var found =
              context
                  .getBean(CustomerRepository.class)
                  .findAll(
                      customers.allOf(
                          List.of(
                              SearchCriteria.of("orderRef", LIKE, "ann"),
                              SearchCriteria.of("orderRef", LIKE, "a-"))));

          assertThat(found).extracting(Customer::getName).containsExactly("Ann");
        });
  }

  @Test
  void should_convert_with_the_conversion_service_it_is_given() {
    var strict =
        SpecificationBuilder.of(Order.class, "placedAt")
            .withConversionService(new DefaultConversionService());

    runner.run(
        context -> {
          seed(context);
          var spec = strict.where(SearchCriteria.of("placedAt", GT, "2026-01-01T00:00:00Z"));

          assertThatThrownBy(() -> context.getBean(OrderRepository.class).findAll(spec))
              .hasRootCauseInstanceOf(InvalidSearchException.class);
        });
  }

  @Test
  void should_not_restrict_when_any_of_gets_no_criteria() {
    runner.run(
        context -> {
          seed(context);

          var found = context.getBean(CustomerRepository.class).findAll(customers.anyOf(List.of()));

          assertThat(found).hasSize(2);
        });
  }

  private static void seed(AssertableApplicationContext context) {
    var people = context.getBean(CustomerRepository.class);
    var ann = people.save(new Customer("Ann", new Contact("ann@example.com")));
    var max = people.save(new Customer("Max", new Contact("max@example.com")));
    var orders = context.getBean(OrderRepository.class);
    orders.save(new Order("A-Ann", OrderStatus.OPEN, BigDecimal.ONE, PLACED, ann));
    orders.save(new Order("Bag-Ann", OrderStatus.OPEN, BigDecimal.ONE, PLACED, ann));
    orders.save(new Order("A-Max", OrderStatus.OPEN, BigDecimal.ONE, PLACED, max));
  }
}
