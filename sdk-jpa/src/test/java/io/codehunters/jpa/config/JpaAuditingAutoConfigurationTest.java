package io.codehunters.jpa.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.jpa.auditing.ClockDateTimeProvider;
import io.codehunters.jpa.auditing.SecurityContextAuditorAware;
import io.codehunters.jpa.fixture.JpaFixtures;
import io.codehunters.jpa.fixture.Order;
import io.codehunters.jpa.fixture.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

class JpaAuditingAutoConfigurationTest {

  private static final Instant NOW = Instant.parse("2026-10-07T10:15:30Z");

  private final ApplicationContextRunner runner = JpaFixtures.h2();

  @Configuration(proxyBeanMethods = false)
  static class SystemAuditor {
    @Bean
    AuditorAware<String> systemAuditor() {
      return () -> Optional.of("system");
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableJpaAuditing
  static class OwnAuditing {}

  @Test
  void should_audit_with_a_clock_and_the_security_context_when_jpa_is_present() {
    runner.run(
        context -> {
          assertThat(context).hasBean("jpaAuditingHandler");
          assertThat(context)
              .getBean(DateTimeProvider.class)
              .isInstanceOf(ClockDateTimeProvider.class);
          assertThat(context)
              .getBean(AuditorAware.class)
              .isInstanceOf(SecurityContextAuditorAware.class);
        });
  }

  @Test
  void should_read_the_time_from_the_application_clock() {
    runner
        .withBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC))
        .run(context -> assertThat(savedOrder(context).getCreatedAt()).contains(NOW));
  }

  @Test
  void should_use_the_application_auditor_when_it_defines_one() {
    runner
        .withUserConfiguration(SystemAuditor.class)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(SecurityContextAuditorAware.class);
              assertThat(savedOrder(context).getCreatedBy()).contains("system");
            });
  }

  @Test
  void should_step_aside_when_the_application_enables_jpa_auditing_itself() {
    runner
        .withUserConfiguration(OwnAuditing.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ClockDateTimeProvider.class);
            });
  }

  @Test
  void should_not_audit_when_disabled() {
    runner
        .withPropertyValues("codehunters.jpa.auditing.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean("jpaAuditingHandler");
              assertThat(savedOrder(context).getCreatedAt()).isEmpty();
            });
  }

  @Test
  void should_audit_without_an_author_when_spring_security_is_absent() {
    runner
        .withClassLoader(new FilteredClassLoader("org.springframework.security"))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(AuditorAware.class);
              assertThat(savedOrder(context).getCreatedAt()).isPresent();
            });
  }

  @Test
  void should_stay_off_when_the_application_has_no_jpa() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JpaAuditingAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(DateTimeProvider.class));
  }

  private static Order savedOrder(AssertableApplicationContext context) {
    var orders = context.getBean(OrderRepository.class);
    return orders.findById(orders.save(new Order("A-1")).getId().orElseThrow()).orElseThrow();
  }
}
