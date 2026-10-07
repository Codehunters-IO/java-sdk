package io.codehunters.jpa.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.jpa.fixture.JpaFixtures;
import io.codehunters.jpa.fixture.Order;
import io.codehunters.jpa.fixture.OrderRepository;
import io.codehunters.jpa.fixture.ShippingAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;

class JsonConvertersPostgresIT {

  private static final ShippingAddress ADDRESS = new ShippingAddress("Calle 1", "Bogota", 3);

  private final ApplicationContextRunner runner = JpaFixtures.postgres();

  @Test
  void should_store_collections_and_values_as_json_and_load_them_back() {
    runner.run(
        context -> {
          var orders = context.getBean(OrderRepository.class);
          var order = new Order("A-1");
          order.describe(List.of("gift", "fragile"), Map.of("size", "XL"), ADDRESS);
          var id = orders.save(order).id().orElseThrow();

          var stored = orders.findById(id).orElseThrow();

          assertThat(stored.getTags()).containsExactly("gift", "fragile");
          assertThat(stored.getAttributes()).containsExactly(Map.entry("size", "XL"));
          assertThat(stored.getShippingAddress()).isEqualTo(ADDRESS);
        });
  }

  @Test
  void should_write_plain_json_text_to_the_column() {
    runner.run(
        context -> {
          var order = new Order("A-1");
          order.describe(List.of("gift"), Map.of(), null);
          context.getBean(OrderRepository.class).save(order);

          var column =
              JdbcClient.create(context.getBean(javax.sql.DataSource.class))
                  .sql("select tags from orders")
                  .query(String.class)
                  .single();

          assertThat(column).isEqualTo("[\"gift\"]");
        });
  }

  @Test
  void should_not_write_or_bump_the_version_of_an_unchanged_entity_on_flush() {
    runner.run(
        context -> {
          var orders = context.getBean(OrderRepository.class);
          var order = new Order("A-1");
          order.describe(List.of("gift"), Map.of("size", "XL"), ADDRESS);
          var id = orders.save(order).id().orElseThrow();
          var before = orders.findById(id).orElseThrow().version();

          var tx =
              new org.springframework.transaction.support.TransactionTemplate(
                  context.getBean(
                      org.springframework.transaction.PlatformTransactionManager.class));
          tx.executeWithoutResult(status -> orders.findById(id).orElseThrow());

          assertThat(orders.findById(id).orElseThrow().version()).isEqualTo(before);
        });
  }

  @Test
  void should_persist_a_replaced_collection() {
    runner.run(
        context -> {
          var orders = context.getBean(OrderRepository.class);
          var order = new Order("A-1");
          order.describe(List.of("gift"), Map.of(), ADDRESS);
          var id = orders.save(order).id().orElseThrow();
          var loaded = orders.findById(id).orElseThrow();
          loaded.describe(List.of("express"), Map.of(), ADDRESS);

          orders.save(loaded);

          assertThat(orders.findById(id).orElseThrow().getTags()).containsExactly("express");
        });
  }
}
