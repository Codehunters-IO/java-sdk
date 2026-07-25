package io.codehunters.audit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Spring Boot auto-configuration enabling the {@link Auditable} aspect. Registers a no-op {@link
 * AuditContextProvider} and the {@link AuditAspect} when an {@link AuditEventPublisherPort} bean is
 * present.
 */
@AutoConfiguration
@EnableAspectJAutoProxy
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
   * @return the {@link AuditAspect}
   */
  @Bean
  @ConditionalOnBean(AuditEventPublisherPort.class)
  public AuditAspect auditAspect(
      AuditEventPublisherPort publisher, AuditContextProvider contextProvider) {
    return new AuditAspect(publisher, contextProvider);
  }
}
