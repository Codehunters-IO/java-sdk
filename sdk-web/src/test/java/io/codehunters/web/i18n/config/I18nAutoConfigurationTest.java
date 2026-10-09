package io.codehunters.web.i18n.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.web.i18n.LocaleMdcFilter;
import io.codehunters.web.i18n.XLocaleAwareLocaleResolver;
import jakarta.servlet.DispatcherType;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;

@ExtendWith(OutputCaptureExtension.class)
class I18nAutoConfigurationTest {

  private static final Locale ENGLISH = Locale.of("en");
  private static final Locale SPANISH = Locale.of("es");

  private final WebApplicationContextRunner webRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(I18nAutoConfiguration.class));

  private final ApplicationContextRunner plainRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(I18nAutoConfiguration.class));

  @AfterEach
  void resetLocale() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void should_register_all_beans_when_running_in_a_servlet_application() {
    webRunner.run(
        context -> {
          assertThat(context.getBean("messageSource"))
              .isInstanceOf(ReloadableResourceBundleMessageSource.class);
          assertThat(context.getBean("localeResolver"))
              .isInstanceOf(XLocaleAwareLocaleResolver.class);
          assertThat(context).hasSingleBean(FilterRegistrationBean.class);
          assertThat(context).doesNotHaveBean(Validator.class);
        });
  }

  @Test
  void should_skip_web_beans_when_application_is_not_a_servlet_application() {
    plainRunner.run(
        context -> {
          assertThat(context).hasBean("messageSource");
          assertThat(context).doesNotHaveBean(LocaleResolver.class);
          assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
        });
  }

  @Test
  void should_back_off_entirely_when_disabled() {
    webRunner
        .withPropertyValues("codehunters.web.i18n.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(I18nProperties.class);
              assertThat(context).doesNotHaveBean(LocaleResolver.class);
              assertThat(context).doesNotHaveBean(LocalValidatorFactoryBean.class);
            });
  }

  @Test
  void should_read_messages_from_configured_basenames() {
    plainRunner
        .withPropertyValues(
            "codehunters.web.i18n.basename=classpath:i18n/messages,classpath:i18n/extra")
        .run(
            context -> {
              assertThat(context.getMessage("greeting", null, ENGLISH)).isEqualTo("Hello");
              assertThat(context.getMessage("farewell", null, ENGLISH)).isEqualTo("Bye");
            });
  }

  @Test
  void should_use_default_locale_bundle_when_requested_locale_has_none() {
    plainRunner
        .withPropertyValues("codehunters.web.i18n.default-locale=en")
        .run(
            context ->
                assertThat(context.getMessage("greeting", null, Locale.of("fr")))
                    .isEqualTo("Hello"));
  }

  @Test
  void should_apply_locale_properties_to_the_resolver() {
    webRunner
        .withPropertyValues(
            "codehunters.web.i18n.default-locale=en",
            "codehunters.web.i18n.supported-locales=en,es")
        .run(
            context -> {
              var resolver = context.getBean(XLocaleAwareLocaleResolver.class);
              assertThat(resolver.resolveLocale(new MockHttpServletRequest())).isEqualTo(ENGLISH);
              assertThat(resolver.getSupportedLocales()).containsExactly(ENGLISH, SPANISH);
            });
  }

  @Test
  void should_back_off_message_source_when_application_defines_one_named_message_source() {
    plainRunner
        .withUserConfiguration(NamedMessageSourceConfig.class)
        .run(
            context ->
                assertThat(context.getBean("messageSource"))
                    .isInstanceOf(StaticMessageSource.class));
  }

  @Test
  void should_keep_its_message_source_when_application_message_source_has_another_name() {
    plainRunner
        .withUserConfiguration(OtherMessageSourceConfig.class)
        .run(
            context ->
                assertThat(context.getBean("messageSource"))
                    .isInstanceOf(ReloadableResourceBundleMessageSource.class));
  }

  @Test
  void should_warn_when_spring_messages_basename_is_set(CapturedOutput output) {
    plainRunner
        .withPropertyValues("spring.messages.basename=messages")
        .run(
            context ->
                assertThat(output).contains("spring.messages.*").contains("codehunters.web.i18n"));
  }

  @Test
  void should_back_off_locale_resolver_when_application_defines_one() {
    webRunner
        .withUserConfiguration(LocaleResolverConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(LocaleResolver.class);
              assertThat(context.getBean(LocaleResolver.class))
                  .isNotInstanceOf(XLocaleAwareLocaleResolver.class);
            });
  }

  @Test
  void should_register_filter_at_configured_order_when_running_in_a_servlet_application() {
    webRunner.run(
        context -> {
          FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
          assertThat(registration.getOrder())
              .isEqualTo(I18nAutoConfiguration.LOCALE_MDC_FILTER_ORDER);
          assertThat(registration.getFilter()).isInstanceOf(LocaleMdcFilter.class);
          assertThat(registration.getUrlPatterns()).contains("/*");
          assertThat(registration.determineDispatcherTypes())
              .containsExactlyInAnyOrder(
                  DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        });
  }

  @Test
  void should_warn_when_spring_web_locale_is_set(CapturedOutput output) {
    webRunner
        .withPropertyValues("spring.web.locale=en")
        .run(
            context ->
                assertThat(output).contains("spring.web.locale").contains("codehunters.web.i18n"));
  }

  @Test
  void should_warn_when_spring_web_locale_resolver_is_set(CapturedOutput output) {
    webRunner
        .withPropertyValues("spring.web.locale-resolver=fixed")
        .run(context -> assertThat(output).contains("spring.web.locale-resolver"));
  }

  @Test
  void should_resolve_constraint_messages_through_boot_validator() {
    LocaleContextHolder.setLocale(ENGLISH);

    plainRunner
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(Validator.class).hasBean("defaultValidator");
              assertThat(context.getBean(Validator.class).validate(new Order(null)))
                  .extracting(violation -> violation.getMessage())
                  .containsExactly("The order id is required");
            });
  }

  @Test
  void should_take_precedence_over_boot_auto_configurations() {
    new WebApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                I18nAutoConfiguration.class,
                MessageSourceAutoConfiguration.class,
                ValidationAutoConfiguration.class,
                WebMvcAutoConfiguration.class))
        .withPropertyValues("spring.messages.basename=i18n/extra")
        .run(
            context -> {
              assertThat(context.getBean("messageSource"))
                  .isInstanceOf(ReloadableResourceBundleMessageSource.class);
              assertThat(context.getBean("localeResolver"))
                  .isInstanceOf(XLocaleAwareLocaleResolver.class);
              assertThat(context).hasSingleBean(Validator.class).hasBean("defaultValidator");
            });
  }

  record Order(@NotNull(message = "{order.id.required}") String id) {}

  @Configuration(proxyBeanMethods = false)
  static class NamedMessageSourceConfig {
    @Bean
    MessageSource messageSource() {
      return new StaticMessageSource();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OtherMessageSourceConfig {
    @Bean
    MessageSource customSource() {
      return new StaticMessageSource();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class LocaleResolverConfig {
    @Bean
    LocaleResolver localeResolver() {
      return new org.springframework.web.servlet.i18n.FixedLocaleResolver(ENGLISH);
    }
  }
}
