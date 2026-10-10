package io.codehunters.jpa.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.jpa.fixture.JpaFixtures;
import io.codehunters.jpa.fixture.MovableClock;
import io.codehunters.jpa.fixture.Order;
import io.codehunters.jpa.fixture.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditableEntityPostgresIT {

  private static final Instant CREATED = Instant.parse("2026-10-07T10:15:30.123456Z");
  private static final Instant UPDATED = Instant.parse("2026-10-07T11:00:00Z");

  private final MovableClock clock = new MovableClock(CREATED);

  private final ApplicationContextRunner runner =
      JpaFixtures.postgres()
          .withPropertyValues("codehunters.jpa.auditing.enabled=true")
          .withBean(Clock.class, () -> clock);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_record_who_created_the_entity_and_when() {
    signIn("alice");

    runner.run(
        context -> {
          var orders = context.getBean(OrderRepository.class);
          var id = orders.save(new Order("A-1")).id().orElseThrow();

          var stored = orders.findById(id).orElseThrow();

          assertThat(stored.createdAt()).contains(CREATED);
          assertThat(stored.createdBy()).contains("alice");
          assertThat(stored.updatedAt()).contains(CREATED);
          assertThat(stored.updatedBy()).contains("alice");
        });
  }

  @Test
  void should_record_the_last_change_and_keep_the_creation_when_the_entity_is_updated() {
    signIn("alice");

    runner.run(
        context -> {
          var orders = context.getBean(OrderRepository.class);
          var id = orders.save(new Order("A-1")).id().orElseThrow();
          var order = orders.findById(id).orElseThrow();
          order.changeReference("A-2");
          clock.moveTo(UPDATED);
          signIn("bob");

          orders.save(order);
          var stored = orders.findById(id).orElseThrow();

          assertThat(stored.createdAt()).contains(CREATED);
          assertThat(stored.createdBy()).contains("alice");
          assertThat(stored.updatedAt()).contains(UPDATED);
          assertThat(stored.updatedBy()).contains("bob");
        });
  }

  @Test
  void should_leave_the_author_empty_when_nobody_is_signed_in() {
    runner.run(
        context -> {
          var orders = context.getBean(OrderRepository.class);
          var id = orders.save(new Order("A-1")).id().orElseThrow();

          var stored = orders.findById(id).orElseThrow();

          assertThat(stored.createdBy()).isEmpty();
          assertThat(stored.createdAt()).contains(CREATED);
        });
  }

  private static void signIn(String name) {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken(name, "n/a", "ROLE_USER"));
  }
}
