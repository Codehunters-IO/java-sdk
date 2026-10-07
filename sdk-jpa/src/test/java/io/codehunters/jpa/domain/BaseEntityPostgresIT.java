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
                    return new ProxyView(equal, Hibernate.isInitialized(proxy), proxy.hashCode());
                  });

          assertThat(view.equal()).isTrue();
          assertThat(view.initializedByEquals()).isFalse();
          assertThat(view.proxyHash()).isEqualTo(loaded.hashCode());
        });
  }

  private record ProxyView(boolean equal, boolean initializedByEquals, int proxyHash) {}

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
