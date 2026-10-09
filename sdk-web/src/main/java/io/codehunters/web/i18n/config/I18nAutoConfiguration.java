package io.codehunters.web.i18n.config;

import io.codehunters.web.i18n.LocaleMdcFilter;
import io.codehunters.web.i18n.XLocaleAwareLocaleResolver;
import jakarta.servlet.DispatcherType;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Registers {@link XLocaleAwareLocaleResolver} as the servlet {@code localeResolver}, in place of
 * the {@code AcceptHeaderLocaleResolver} Boot's Web MVC auto-configuration would register. The
 * default locale is Boot's {@code spring.web.locale}; without it, the container's locale applies,
 * as with Boot. Message bundles are left to Boot ({@code spring.messages.*}).
 *
 * <p>Backs off when the application declares a bean named {@code localeResolver}, when {@code
 * spring.web.locale-resolver=fixed} (Boot's fixed resolver then applies), or with {@code
 * codehunters.web.i18n.enabled=false}. The locale-to-MDC filter is opt-in: {@code
 * codehunters.web.i18n.mdc.enabled=true}.
 */
@AutoConfiguration(
    beforeName = "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration")
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(LocaleResolver.class)
@ConditionalOnBooleanProperty(name = "codehunters.web.i18n.enabled", matchIfMissing = true)
@EnableConfigurationProperties(I18nProperties.class)
public class I18nAutoConfiguration {

  /**
   * Filter order: early, near {@link Ordered#HIGHEST_PRECEDENCE}, so later filters and their log
   * lines see the {@code locale} MDC key. The key lives on the request thread only; {@code @Async}
   * work sees it only when the application propagates the MDC (e.g. Micrometer's {@code
   * Slf4jThreadLocalAccessor} with a context-propagating task decorator).
   */
  public static final int LOCALE_MDC_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public I18nAutoConfiguration() {}

  @Bean
  @ConditionalOnMissingBean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
  @Conditional(AcceptHeaderResolution.class)
  LocaleResolver localeResolver(I18nProperties properties, Environment environment) {
    var resolver =
        new XLocaleAwareLocaleResolver(properties.headerName(), properties.supportedLocales());
    Binder.get(environment)
        .bind("spring.web.locale", Locale.class)
        .ifBound(resolver::setDefaultLocale);
    return resolver;
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(FilterRegistrationBean.class)
  @ConditionalOnBooleanProperty("codehunters.web.i18n.mdc.enabled")
  static class MdcConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "localeMdcFilterRegistration")
    FilterRegistrationBean<LocaleMdcFilter> localeMdcFilterRegistration(
        @Qualifier(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME) LocaleResolver localeResolver) {
      var registration = new FilterRegistrationBean<>(new LocaleMdcFilter(localeResolver));
      registration.addUrlPatterns("/*");
      registration.setOrder(LOCALE_MDC_FILTER_ORDER);
      registration.setName("localeMdcFilter");
      registration.setDispatcherTypes(
          DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
      return registration;
    }
  }

  /** Matches unless Boot is told to use its fixed resolver ({@code spring.web.locale-resolver}). */
  static final class AcceptHeaderResolution extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(
        ConditionContext context, AnnotatedTypeMetadata metadata) {
      String strategy = context.getEnvironment().getProperty("spring.web.locale-resolver", "");
      return "fixed".equalsIgnoreCase(strategy.strip())
          ? ConditionOutcome.noMatch("spring.web.locale-resolver is fixed")
          : ConditionOutcome.match("spring.web.locale-resolver is not fixed");
    }
  }
}
