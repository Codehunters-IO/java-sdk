package io.codehunters.jpa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.jpa.fixture.Customer;
import io.codehunters.jpa.fixture.CustomerRepository;
import io.codehunters.jpa.fixture.JpaFixtures;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class BaseEntityPostgresIT {

  private final ApplicationContextRunner runner = JpaFixtures.postgres();

  @Test
  void should_assign_a_time_ordered_uuid_version_7_when_persisted() {
    runner.run(
        context -> {
          var saved = customers(context).save(new Customer("Ada"));

          assertThat(saved.getId()).hasValueSatisfying(id -> assertThat(id.version()).isEqualTo(7));
          assertThat(saved.isNew()).isFalse();
        });
  }

  @Test
  void should_start_the_version_at_zero_when_persisted() {
    runner.run(
        context ->
            assertThat(customers(context).save(new Customer("Ada")).getVersion()).contains(0L));
  }

  @Test
  void should_reject_a_stale_copy_when_another_update_committed_first() {
    runner.run(
        context -> {
          var repository = customers(context);
          var id = idOf(repository.save(new Customer("Ada")));
          var first = repository.findById(id).orElseThrow();
          var stale = repository.findById(id).orElseThrow();
          first.rename("Ada L.");
          repository.save(first);
          stale.rename("Ada Lovelace");

          assertThatThrownBy(() -> repository.save(stale))
              .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        });
  }

  @Test
  void should_equal_an_uninitialized_proxy_of_the_same_row() {
    runner.run(
        context -> {
          var id = idOf(customers(context).save(new Customer("Ada")));
          var loaded = customers(context).findById(id).orElseThrow();

          var view =
              inTransaction(
                  context,
                  em -> {
                    var proxy = em.getReference(Customer.class, id);
                    var equal = loaded.equals(proxy);
                    var hash = proxy.hashCode();
                    return new ProxyView(equal, Hibernate.isInitialized(proxy), hash);
                  });

          assertThat(view.equal()).isTrue();
          assertThat(view.initialized()).isFalse();
          assertThat(view.proxyHash()).isEqualTo(loaded.hashCode());
        });
  }

  @Test
  void should_compare_from_an_uninitialized_proxy_without_loading_it() {
    runner.run(
        context -> {
          var id = idOf(customers(context).save(new Customer("Ada")));
          var loaded = customers(context).findById(id).orElseThrow();

          var view =
              inTransaction(
                  context,
                  em -> {
                    var proxy = em.getReference(Customer.class, id);
                    var equal = proxy.equals(loaded);
                    return new ProxyView(equal, Hibernate.isInitialized(proxy), proxy.hashCode());
                  });

          assertThat(view.equal()).isTrue();
          assertThat(view.initialized()).isFalse();
        });
  }

  @Test
  void should_compare_and_identify_a_detached_proxy_without_a_session() {
    runner.run(
        context -> {
          var id = idOf(customers(context).save(new Customer("Ada")));
          var loaded = customers(context).findById(id).orElseThrow();

          var proxy = inTransaction(context, em -> em.getReference(Customer.class, id));

          assertThat(proxy.equals(loaded)).isTrue();
          assertThat(proxy.hashCode()).isEqualTo(loaded.hashCode());
          assertThat(proxy.getId()).contains(id);
        });
  }

  private record ProxyView(boolean equal, boolean initialized, int proxyHash) {}

  @Test
  void should_persist_and_manage_the_instance_it_saves_when_the_entity_is_new() {
    runner.run(
        context -> {
          var view =
              inTransaction(
                  context,
                  em -> {
                    var customer = new Customer("Ada");
                    customers(context).save(customer);
                    return new SaveView(customer.getId().isPresent(), em.contains(customer));
                  });

          assertThat(view.idAssigned()).isTrue();
          assertThat(view.managed()).isTrue();
        });
  }

  private record SaveView(boolean idAssigned, boolean managed) {}

  @Test
  void should_delete_a_saved_entity_through_the_repository() {
    runner.run(
        context -> {
          var repository = customers(context);
          var saved = repository.save(new Customer("Ada"));

          repository.delete(saved);

          assertThat(repository.count()).isZero();
        });
  }

  @Test
  void should_not_equal_another_row() {
    runner.run(
        context ->
            assertThat(customers(context).save(new Customer("Ada")))
                .isNotEqualTo(customers(context).save(new Customer("Ada"))));
  }

  @Test
  void should_still_find_an_entity_in_a_hash_set_after_it_was_persisted() {
    runner.run(
        context -> {
          var customer = new Customer("Ada");
          Set<Customer> set = new HashSet<>(Set.of(customer));

          customers(context).save(customer);

          assertThat(customer.getId()).isPresent();
          assertThat(set).contains(customer);
        });
  }

  private static CustomerRepository customers(AssertableApplicationContext context) {
    return context.getBean(CustomerRepository.class);
  }

  private static UUID idOf(Customer customer) {
    return customer.getId().orElseThrow();
  }

  private static <T> T inTransaction(
      AssertableApplicationContext context, java.util.function.Function<EntityManager, T> work) {
    var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    return tx.execute(status -> work.apply(context.getBean(EntityManager.class)));
  }
}
