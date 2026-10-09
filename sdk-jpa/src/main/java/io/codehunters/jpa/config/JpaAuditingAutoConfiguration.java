package io.codehunters.jpa.config;

import io.codehunters.jpa.auditing.ClockDateTimeProvider;
import io.codehunters.jpa.auditing.SecurityContextAuditorAware;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing for {@link io.codehunters.jpa.entity.AuditableEntity}: the time
 * comes from the application's {@link Clock} (UTC system clock otherwise) and the author from
 * Spring Security when it is on the classpath.
 *
 * <p>It steps aside entirely when the application declares {@code @EnableJpaAuditing} itself, and
 * with {@code codehunters.jpa.auditing.enabled=false}. An application {@link DateTimeProvider} or
 * {@link AuditorAware} bean replaces the SDK's.
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
@ConditionalOnClass(
    name = {
      "org.springframework.data.jpa.repository.config.EnableJpaAuditing",
      "jakarta.persistence.EntityManagerFactory"
    })
@ConditionalOnBean(type = "jakarta.persistence.EntityManagerFactory")
@ConditionalOnBooleanProperty(name = "codehunters.jpa.auditing.enabled", matchIfMissing = true)
@EnableConfigurationProperties(JpaAuditingProperties.class)
public class JpaAuditingAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public JpaAuditingAutoConfiguration() {}

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(name = "jpaAuditingHandler")
  @EnableJpaAuditing
  static class AuditingConfiguration {

    @Bean
    @ConditionalOnMissingBean(DateTimeProvider.class)
    ClockDateTimeProvider codehuntersJpaDateTimeProvider(ObjectProvider<Clock> clock) {
      return new ClockDateTimeProvider(clock.getIfUnique(Clock::systemUTC));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    static class SecurityAuditorConfiguration {

      @Bean
      @ConditionalOnMissingBean(AuditorAware.class)
      SecurityContextAuditorAware codehuntersJpaAuditorAware() {
        return new SecurityContextAuditorAware();
      }
    }
  }
}
