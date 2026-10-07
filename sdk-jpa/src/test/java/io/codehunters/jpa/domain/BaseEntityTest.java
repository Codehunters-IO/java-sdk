package io.codehunters.jpa.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.jpa.fixture.Customer;
import io.codehunters.jpa.fixture.Order;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

  @Test
  void should_equal_itself_when_not_yet_persisted() {
    var customer = new Customer("Ada");

    assertThat(customer).isEqualTo(customer);
  }

  @Test
  void should_not_equal_another_transient_entity_when_neither_has_an_id() {
    assertThat(new Customer("Ada")).isNotEqualTo(new Customer("Ada"));
  }

  @Test
  void should_not_equal_null_or_another_type() {
    var customer = new Customer("Ada");

    assertThat(customer.equals(null)).isFalse();
    assertThat(customer.equals("Ada")).isFalse();
  }

  @Test
  void should_share_one_hash_code_per_entity_class_so_it_survives_persisting() {
    assertThat(new Customer("Ada").hashCode()).isEqualTo(new Customer("Grace").hashCode());
  }

  @Test
  void should_report_new_until_an_id_is_assigned() {
    var customer = new Customer("Ada");

    assertThat(customer.isNew()).isTrue();
    assertThat(customer.id()).isEmpty();
    assertThat(customer.version()).isEmpty();
  }

  @Test
  void should_not_equal_an_entity_of_another_class() {
    assertThat(new Customer("Ada")).isNotEqualTo(new Order("A-1"));
  }
}
