package io.codehunters.audit;

import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Spring Boot auto-configuration enabling the {@link Auditable} aspect. Registers a no-op {@link
 * AuditContextProvider} and the {@link AuditAspect} when an {@link AuditEventPublisherPort} bean is
 * present.
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(AuditProperties.class)
public class AuditAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public AuditAutoConfiguration() {}

  /**
   * Provides a default no-op context provider when none is defined.
   *
   * @return a {@link NoOpAuditContextProvider}
   */
  @Bean
  @ConditionalOnMissingBean
  public AuditContextProvider auditContextProvider() {
    return new NoOpAuditContextProvider();
  }

  /**
   * Registers the auditing aspect.
   *
   * @param publisher the audit event publisher
   * @param contextProvider the audit context provider
   * @param clock the application's {@link Clock}, if it defines exactly one; UTC otherwise
   * @param properties the audit configuration, for fail-on-error and the aspect's order
   * @return the {@link AuditAspect}
   */
  @Bean
  @ConditionalOnBean(AuditEventPublisherPort.class)
  public AuditAspect auditAspect(
      AuditEventPublisherPort publisher,
      AuditContextProvider contextProvider,
      ObjectProvider<Clock> clock,
      AuditProperties properties) {
    return new AuditAspect(
        publisher,
        contextProvider,
        new AuditAspect.Options(
            clock.getIfUnique(Clock::systemUTC), properties.failOnError(), properties.order()));
  }
}
