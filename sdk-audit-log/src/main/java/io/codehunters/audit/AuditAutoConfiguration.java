package io.codehunters.audit;

import java.time.Clock;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration enabling the {@link Auditable} aspect. Registers a no-op {@link
 * AuditContextProvider} and the {@link AuditAspect} when an {@link AuditEventPublisherPort} bean is
 * present.
 *
 * <p>The aspect is applied by Boot's AOP auto-configuration ({@code spring.aop.auto}, on by default
 * with AspectJ on the classpath); this auto-configuration does not enable proxying itself. With
 * {@code spring.aop.auto=false} the application must enable AspectJ proxying for auditing to apply;
 * a startup check warns when it does not, or fails startup when {@code fail-on-error} is set.
 */
@AutoConfiguration
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
  @ConditionalOnClass(name = "org.aspectj.weaver.Advice")
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

  /**
   * Checks at startup that an AspectJ auto-proxy creator applies the {@link AuditAspect}; warns, or
   * fails when {@code fail-on-error} is set, if auditing would silently do nothing.
   *
   * @param beanFactory the application's bean factory
   * @param properties the audit configuration, for fail-on-error
   * @return the startup check
   */
  @Bean
  @ConditionalOnClass(name = "org.aspectj.weaver.Advice")
  @ConditionalOnBean(AuditEventPublisherPort.class)
  static SmartInitializingSingleton auditProxyCheck(
      ListableBeanFactory beanFactory, AuditProperties properties) {
    return new AuditProxyCheck(beanFactory, properties.failOnError());
  }
}
