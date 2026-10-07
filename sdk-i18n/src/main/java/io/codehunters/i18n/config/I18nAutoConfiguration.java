package io.codehunters.i18n.config;

import io.codehunters.i18n.LocaleMdcFilter;
import io.codehunters.i18n.XLocaleAwareLocaleResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Spring Boot auto-configuration for internationalization, in two independent parts: the message
 * source and the servlet locale resolver with its locale-to-MDC filter. Bean validation needs no
 * part of its own: Boot's validator resolves {@code {key}} messages through this message source.
 * Runs before Boot's message source and Web MVC auto-configurations, so the {@code
 * codehunters.i18n.*} settings take precedence: while this SDK provides the {@code messageSource}
 * bean, {@code spring.messages.*} is ignored. Set {@code codehunters.i18n.enabled=false} or declare
 * a bean named {@code messageSource} to use Boot's.
 */
@AutoConfiguration(
    beforeName = {
      "org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration",
      "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration"
    })
@ConditionalOnBooleanProperty(name = "codehunters.i18n.enabled", matchIfMissing = true)
@EnableConfigurationProperties(I18nProperties.class)
public class I18nAutoConfiguration {

  /**
   * Filter order: after the tracing filter, which runs near {@link Ordered#HIGHEST_PRECEDENCE}.
   * Both filters write to MDC; locale piggybacks on the same async-propagation mechanism.
   */
  public static final int LOCALE_MDC_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public I18nAutoConfiguration() {}

  @Configuration(proxyBeanMethods = false)
  static class MessageSourceConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(I18nAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(
        name = AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME,
        search = SearchStrategy.CURRENT)
    MessageSource messageSource(I18nProperties properties, Environment environment) {
      warnWhenBootMessagesAreConfigured(environment);
      var bean = new ReloadableResourceBundleMessageSource();
      bean.setBasenames(properties.basename().toArray(String[]::new));
      bean.setDefaultEncoding(properties.encoding().name());
      bean.setDefaultLocale(properties.defaultLocale());
      bean.setFallbackToSystemLocale(properties.fallbackToSystemLocale());
      return bean;
    }

    private static void warnWhenBootMessagesAreConfigured(Environment environment) {
      if (environment.containsProperty("spring.messages.basename")) {
        LOG.warn(
            "spring.messages.* is ignored: the message source is configured by codehunters.i18n.*");
      }
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = Type.SERVLET)
  @ConditionalOnClass({LocaleResolver.class, FilterRegistrationBean.class})
  static class ServletConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    LocaleResolver localeResolver(I18nProperties properties) {
      return new XLocaleAwareLocaleResolver(
          properties.defaultLocale(), properties.headerName(), properties.supportedLocales());
    }

    @Bean
    @ConditionalOnMissingBean(name = "localeMdcFilterRegistration")
    FilterRegistrationBean<LocaleMdcFilter> localeMdcFilterRegistration(
        @Qualifier(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME) LocaleResolver localeResolver) {
      var registration = new FilterRegistrationBean<>(new LocaleMdcFilter(localeResolver));
      registration.addUrlPatterns("/*");
      registration.setOrder(LOCALE_MDC_FILTER_ORDER);
      registration.setName("localeMdcFilter");
      return registration;
    }
  }
}
