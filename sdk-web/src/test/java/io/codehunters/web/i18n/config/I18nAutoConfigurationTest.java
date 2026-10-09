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
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

class I18nAutoConfigurationTest {

  private static final Locale ENGLISH = Locale.of("en");
  private static final Locale SPANISH = Locale.of("es");

  private final WebApplicationContextRunner webRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(I18nAutoConfiguration.class));

  private final WebApplicationContextRunner bootRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  I18nAutoConfiguration.class,
                  MessageSourceAutoConfiguration.class,
                  ValidationAutoConfiguration.class,
                  WebMvcAutoConfiguration.class))
          .withPropertyValues("spring.messages.fallback-to-system-locale=false");

  @AfterEach
  void resetLocale() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void should_register_the_header_locale_resolver_when_running_in_a_servlet_application() {
    webRunner.run(
        context ->
            assertThat(context.getBean("localeResolver"))
                .isInstanceOf(XLocaleAwareLocaleResolver.class));
  }

  @Test
  void should_register_no_mdc_filter_when_it_is_not_enabled() {
    webRunner.run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
  }

  @Test
  void should_register_no_message_source_of_its_own() {
    webRunner.run(context -> assertThat(context).doesNotHaveBean("messageSource"));
  }

  @Test
  void should_register_nothing_when_application_is_not_a_servlet_application() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(I18nAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(I18nProperties.class);
              assertThat(context).doesNotHaveBean(LocaleResolver.class);
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
            });
  }

  @Test
  void should_take_the_default_locale_from_spring_web_locale() {
    webRunner
        .withPropertyValues("spring.web.locale=en")
        .run(
            context ->
                assertThat(
                        context
                            .getBean(XLocaleAwareLocaleResolver.class)
                            .resolveLocale(new MockHttpServletRequest()))
                    .isEqualTo(ENGLISH));
  }

  @Test
  void should_use_container_locale_without_headers_when_spring_web_locale_is_not_set() {
    webRunner.run(
        context ->
            assertThat(
                    context
                        .getBean(XLocaleAwareLocaleResolver.class)
                        .resolveLocale(requestWithContainerLocale(Locale.JAPAN)))
                .isEqualTo(Locale.JAPAN));
  }

  // The container's locale without an Accept-Language header is the server's default locale.
  private static MockHttpServletRequest requestWithContainerLocale(Locale locale) {
    return new MockHttpServletRequest() {
      @Override
      public Locale getLocale() {
        return locale;
      }
    };
  }

  @Test
  void should_apply_supported_locales_to_the_resolver() {
    webRunner
        .withPropertyValues("codehunters.web.i18n.supported-locales=en,es")
        .run(
            context ->
                assertThat(context.getBean(XLocaleAwareLocaleResolver.class).getSupportedLocales())
                    .containsExactly(ENGLISH, SPANISH));
  }

  @Test
  void should_back_off_locale_resolver_when_application_defines_one() {
    webRunner
        .withUserConfiguration(LocaleResolverConfig.class)
        .run(
            context ->
                assertThat(context.getBean(LocaleResolver.class))
                    .isInstanceOf(FixedLocaleResolver.class));
  }

  @Test
  void should_leave_boot_fixed_locale_resolver_when_spring_web_locale_resolver_is_fixed() {
    bootRunner
        .withPropertyValues("spring.web.locale-resolver=fixed", "spring.web.locale=en")
        .run(
            context ->
                assertThat(context.getBean("localeResolver"))
                    .isInstanceOf(FixedLocaleResolver.class));
  }

  @Test
  void should_replace_boot_accept_header_resolver_when_spring_web_locale_resolver_is_default() {
    bootRunner
        .withPropertyValues("spring.web.locale-resolver=accept-header")
        .run(
            context ->
                assertThat(context.getBean("localeResolver"))
                    .isInstanceOf(XLocaleAwareLocaleResolver.class));
  }

  @Test
  void should_register_mdc_filter_at_its_order_when_enabled() {
    webRunner
        .withPropertyValues("codehunters.web.i18n.mdc.enabled=true")
        .run(
            context -> {
              FilterRegistrationBean<?> registration =
                  context.getBean(FilterRegistrationBean.class);
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
  void should_keep_boot_message_source_when_spring_messages_basename_is_set() {
    bootRunner
        .withPropertyValues("spring.messages.basename=i18n/messages,i18n/extra")
        .run(
            context -> {
              assertThat(context.getBean("messageSource"))
                  .isInstanceOf(ResourceBundleMessageSource.class);
              assertThat(context.getMessage("greeting", null, SPANISH)).isEqualTo("Hola");
              assertThat(context.getMessage("farewell", null, ENGLISH)).isEqualTo("Bye");
            });
  }

  @Test
  void should_resolve_constraint_messages_through_boot_message_source() {
    LocaleContextHolder.setLocale(SPANISH);

    bootRunner
        .withPropertyValues("spring.messages.basename=i18n/messages")
        .run(
            context ->
                assertThat(context.getBean(Validator.class).validate(new Order(null)))
                    .extracting(violation -> violation.getMessage())
                    .containsExactly("El id del pedido es obligatorio"));
  }

  record Order(@NotNull(message = "{order.id.required}") String id) {}

  @Configuration(proxyBeanMethods = false)
  static class LocaleResolverConfig {
    @Bean
    LocaleResolver localeResolver() {
      return new FixedLocaleResolver(ENGLISH);
    }
  }
}
