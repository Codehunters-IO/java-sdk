package io.codehunters.i18n.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.i18n.LocaleMdcFilter;
import io.codehunters.i18n.XLocaleAwareLocaleResolver;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;

class I18nAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(I18nAutoConfiguration.class));

  @Test
  @DisplayName("should_register_all_beans_when_autoconfig_runs")
  void should_register_all_beans() {
    contextRunner.run(
        context -> {
          assertThat(context).hasBean("messageSource");
          assertThat(context).hasSingleBean(LocaleResolver.class);
          assertThat(context).hasSingleBean(FilterRegistrationBean.class);
          assertThat(context).hasSingleBean(LocalValidatorFactoryBean.class);
          assertThat(context.getBean(LocaleResolver.class))
              .isInstanceOf(XLocaleAwareLocaleResolver.class);
        });
  }

  @Test
  @DisplayName("should_apply_default_properties_when_not_overridden")
  void should_apply_default_properties() {
    contextRunner.run(
        context -> {
          I18nProperties properties = context.getBean(I18nProperties.class);
          assertThat(properties.getBasename()).isEqualTo("classpath:i18n/messages");
          assertThat(properties.getDefaultLocale()).isEqualTo(Locale.of("es"));
          assertThat(properties.getHeaderName()).isEqualTo("X-Locale");
        });
  }

  @Test
  @DisplayName("should_override_properties_when_provided_via_environment")
  void should_override_properties() {
    contextRunner
        .withPropertyValues(
            "vitxo.i18n.basename=classpath:i18n/custom",
            "vitxo.i18n.default-locale=en",
            "vitxo.i18n.header-name=X-Lang",
            "vitxo.i18n.encoding=ISO-8859-1",
            "vitxo.i18n.fallback-to-system-locale=true")
        .run(
            context -> {
              I18nProperties properties = context.getBean(I18nProperties.class);
              assertThat(properties.getBasename()).isEqualTo("classpath:i18n/custom");
              assertThat(properties.getDefaultLocale()).isEqualTo(Locale.of("en"));
              assertThat(properties.getHeaderName()).isEqualTo("X-Lang");
              assertThat(properties.getEncoding()).isEqualTo("ISO-8859-1");
              assertThat(properties.isFallbackToSystemLocale()).isTrue();
            });
  }

  @Test
  @DisplayName("should_back_off_when_user_provides_message_source")
  void should_back_off_when_user_provides_message_source() {
    contextRunner
        .withUserConfiguration(CustomMessageSourceConfig.class)
        .run(
            context -> {
              MessageSource custom = context.getBean("customSource", MessageSource.class);
              assertThat(context.getBeansOfType(MessageSource.class)).containsValue(custom);
              assertThat(context.getBeansOfType(ReloadableResourceBundleMessageSource.class))
                  .hasSize(1)
                  .containsKey("customSource");
            });
  }

  @Test
  @DisplayName("should_register_filter_at_configured_order")
  void should_register_filter_at_configured_order() {
    contextRunner.run(
        context -> {
          FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
          assertThat(registration.getOrder())
              .isEqualTo(I18nAutoConfiguration.LOCALE_MDC_FILTER_ORDER);
          assertThat(registration.getFilter()).isInstanceOf(LocaleMdcFilter.class);
          assertThat(registration.getUrlPatterns()).contains("/*");
        });
  }

  @Configuration
  static class CustomMessageSourceConfig {
    @Bean
    MessageSource customSource() {
      return new ReloadableResourceBundleMessageSource();
    }
  }
}
