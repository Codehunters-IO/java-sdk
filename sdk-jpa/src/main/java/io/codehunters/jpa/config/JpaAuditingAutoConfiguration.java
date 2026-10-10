package io.codehunters.jpa.config;

import io.codehunters.jpa.auditing.ClockDateTimeProvider;
import io.codehunters.jpa.auditing.SecurityContextAuditorAware;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
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
 * Enables Spring Data JPA auditing for {@link io.codehunters.jpa.entity.AuditableEntity} when the
 * application opts in with {@code codehunters.jpa.auditing.enabled=true}: the time comes from the
 * application's {@link Clock} (UTC system clock otherwise) and the author from Spring Security when
 * it is on the classpath.
 *
 * <p>It is off by default because {@code @EnableJpaAuditing} applies to every entity of the
 * application, not only the SDK's. It steps aside entirely when the application declares
 * {@code @EnableJpaAuditing} itself. An application {@link DateTimeProvider} or {@link
 * AuditorAware} bean replaces the SDK's. When it is on it logs it once at startup.
 *
 * <p>Whatever the property, it warns at startup when entities extend {@link
 * io.codehunters.jpa.entity.AuditableEntity} and no auditing is active, from the SDK or from the
 * application's own {@code @EnableJpaAuditing}.
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
@ConditionalOnClass(
    name = {
      "org.springframework.data.jpa.repository.config.EnableJpaAuditing",
      "jakarta.persistence.EntityManagerFactory"
    })
@ConditionalOnBean(type = "jakarta.persistence.EntityManagerFactory")
@EnableConfigurationProperties(JpaAuditingProperties.class)
public class JpaAuditingAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public JpaAuditingAutoConfiguration() {}

  /**
   * Warns at startup when entities extend {@link io.codehunters.jpa.entity.AuditableEntity} but no
   * auditing is active, whether or not the SDK's auditing is enabled.
   *
   * @param beans the application's bean factory
   * @return the startup check
   */
  @Bean
  static SmartInitializingSingleton codehuntersJpaAuditingActiveCheck(ListableBeanFactory beans) {
    return new AuditingActiveCheck(beans);
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnBooleanProperty(name = "codehunters.jpa.auditing.enabled", matchIfMissing = false)
  @ConditionalOnMissingBean(name = "jpaAuditingHandler")
  @EnableJpaAuditing
  static class AuditingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JpaAuditingAutoConfiguration.class);

    AuditingConfiguration() {
      log.info(
          "SDK JPA auditing is on: @EnableJpaAuditing applies to every entity of the application;"
              + " set codehunters.jpa.auditing.enabled=false to turn it off");
    }

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
