package io.codehunters.i18n.config;

import io.codehunters.i18n.LocaleMdcFilter;
import io.codehunters.i18n.XLocaleAwareLocaleResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.Ordered;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Spring Boot auto-configuration for internationalization: message source, locale resolver, the
 * locale-to-MDC filter and a message-aware bean validator.
 */
@AutoConfiguration(before = WebMvcAutoConfiguration.class)
@EnableConfigurationProperties(I18nProperties.class)
public class I18nAutoConfiguration {

  /**
   * Filter order: just after {@code TraceIdFilter} (which uses {@link Ordered#HIGHEST_PRECEDENCE}).
   * Both filters write to MDC; locale piggybacks on the same async-propagation mechanism.
   */
  public static final int LOCALE_MDC_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public I18nAutoConfiguration() {}

  /**
   * Provides the reloadable resource-bundle message source.
   *
   * @param properties the i18n configuration
   * @return a {@link MessageSource}
   */
  @Bean
  @ConditionalOnMissingBean
  public MessageSource messageSource(I18nProperties properties) {
    var bean = new ReloadableResourceBundleMessageSource();
    bean.setBasename(properties.getBasename());
    bean.setDefaultEncoding(properties.getEncoding());
    bean.setDefaultLocale(properties.getDefaultLocale());
    bean.setFallbackToSystemLocale(properties.isFallbackToSystemLocale());
    return bean;
  }

  /**
   * Provides the header-aware locale resolver.
   *
   * @param properties the i18n configuration
   * @return a {@link LocaleResolver}
   */
  @Bean
  @ConditionalOnClass(HttpServletRequest.class)
  @ConditionalOnMissingBean
  public LocaleResolver localeResolver(I18nProperties properties) {
    return new XLocaleAwareLocaleResolver(
        properties.getDefaultLocale(), properties.getHeaderName());
  }

  /**
   * Registers the filter that copies the resolved locale into the MDC.
   *
   * @param localeResolver the locale resolver
   * @return the filter registration
   */
  @Bean
  @ConditionalOnClass(HttpServletRequest.class)
  @ConditionalOnMissingBean(name = "localeMdcFilterRegistration")
  public FilterRegistrationBean<LocaleMdcFilter> localeMdcFilterRegistration(
      LocaleResolver localeResolver) {
    FilterRegistrationBean<LocaleMdcFilter> registration =
        new FilterRegistrationBean<>(new LocaleMdcFilter(localeResolver));
    registration.addUrlPatterns("/*");
    registration.setOrder(LOCALE_MDC_FILTER_ORDER);
    registration.setName("localeMdcFilter");
    return registration;
  }

  /**
   * Provides a bean validator that resolves constraint messages from the message source.
   *
   * @param messageSource the message source
   * @return a {@link LocalValidatorFactoryBean}
   */
  @Bean
  @ConditionalOnMissingBean
  public LocalValidatorFactoryBean validatorFactoryBean(MessageSource messageSource) {
    var validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(messageSource);
    return validator;
  }
}
