package io.codehunters.audit.config;

import io.codehunters.audit.AuditContextProvider;
import io.codehunters.audit.AuditEventPublisherPort;
import io.codehunters.audit.NoOpAuditContextProvider;
import io.codehunters.audit.internal.aspect.AuditAspect;
import io.codehunters.audit.internal.aspect.AuditProxyCheck;
import io.codehunters.audit.internal.aspect.AuditStartupCheck;
import io.codehunters.audit.internal.device.DeviceAuditContextProvider;
import io.codehunters.web.device.servlet.ClientContextResolver;
import java.time.Clock;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration enabling the {@link io.codehunters.audit.Auditable} aspect.
 * Registers a no-op {@link AuditContextProvider} and the {@link AuditAspect} when an {@link
 * AuditEventPublisherPort} bean is present.
 *
 * <p>The aspect is applied by Boot's AOP auto-configuration ({@code spring.aop.auto}, on by default
 * with AspectJ on the classpath); this auto-configuration does not enable proxying itself. With
 * {@code spring.aop.auto=false} the application must enable AspectJ proxying for auditing to apply;
 * a startup check warns when it does not, or fails startup when {@code fail-on-error} is set.
 *
 * <p>When the device SDK is on the classpath of a servlet application, the default context provider
 * reports the IP and {@code User-Agent} of the current request, resolved behind the device SDK's
 * trusted proxies, instead of nothing.
 *
 * <p>At startup, when beans declare {@code @Auditable} methods but no {@link
 * AuditEventPublisherPort} exists, the reason is logged as an error, or fails startup when {@code
 * fail-on-error} is set. {@code codehunters.audit.enabled=false} turns the SDK off.
 */
@AutoConfiguration(afterName = "io.codehunters.web.device.config.DeviceAutoConfiguration")
@ConditionalOnBooleanProperty(name = AuditAutoConfiguration.ENABLED, matchIfMissing = true)
@EnableConfigurationProperties(AuditProperties.class)
public class AuditAutoConfiguration {

  /** The property that turns the SDK off when {@code false}. */
  static final String ENABLED = "codehunters.audit.enabled";

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public AuditAutoConfiguration() {}

  /** Reads the client IP and {@code User-Agent} from the device SDK when it is present. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "io.codehunters.web.device.servlet.ClientContextResolver")
  static class DeviceContextConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditContextProvider.class)
    @ConditionalOnBean(ClientContextResolver.class)
    AuditContextProvider deviceAuditContextProvider(ClientContextResolver resolver) {
      return new DeviceAuditContextProvider(resolver);
    }
  }

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

  /**
   * Checks at startup that {@code @Auditable} methods are actually audited; logs why they are not,
   * or fails when {@code fail-on-error} is set.
   *
   * @param beanFactory the application's bean factory
   * @param properties the audit configuration, for fail-on-error
   * @return the startup check
   */
  @Bean
  static SmartInitializingSingleton auditStartupCheck(
      ConfigurableListableBeanFactory beanFactory, AuditProperties properties) {
    return new AuditStartupCheck(beanFactory, properties.failOnError());
  }
}
