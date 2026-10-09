package io.codehunters.web.errors.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticMessageSource;

/** The SDK's error bundle joins the application's message source, after the application's own. */
class ErrorBundleTest {

  private static final Locale SPANISH = Locale.of("es");
  private static final String SDK_SORT_EN = "The requested sort is not allowed.";

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  WebErrorsAutoConfiguration.class, MessageSourceAutoConfiguration.class))
          .withPropertyValues(
              "spring.messages.basename=i18n/messages",
              "spring.messages.fallback-to-system-locale=false");

  @Test
  void should_resolve_application_messages_when_spring_messages_basename_is_set() {
    runner.run(
        context -> assertThat(context.getMessage("greeting", null, SPANISH)).isEqualTo("Hola"));
  }

  @Test
  void should_resolve_sdk_messages_through_the_application_message_source() {
    runner.run(
        context -> {
          assertThat(context.getMessage("error.INVALID_SORT", null, Locale.ENGLISH))
              .isEqualTo(SDK_SORT_EN);
          assertThat(context.getMessage("error.INVALID_SORT", null, SPANISH))
              .isEqualTo("El orden solicitado no está permitido.");
        });
  }

  @Test
  void should_prefer_application_message_when_both_bundles_define_the_key() {
    runner.run(
        context ->
            assertThat(context.getMessage("error.INVALID_SEARCH", null, Locale.ENGLISH))
                .isEqualTo("The application rejects this search"));
  }

  @Test
  void should_add_the_sdk_bundle_to_a_reloadable_message_source_of_the_application() {
    runner
        .withUserConfiguration(ReloadableMessages.class)
        .run(
            context -> {
              assertThat(context.getBean("messageSource"))
                  .isInstanceOf(ReloadableResourceBundleMessageSource.class);
              assertThat(context.getMessage("error.INVALID_SORT", null, Locale.ENGLISH))
                  .isEqualTo(SDK_SORT_EN);
            });
  }

  @Test
  void should_leave_a_message_source_without_basenames_untouched() {
    runner
        .withUserConfiguration(StaticMessages.class)
        .run(
            context ->
                assertThat(context.getMessage("error.INVALID_SORT", null, "none", Locale.ENGLISH))
                    .isEqualTo("none"));
  }

  @Test
  void should_leave_other_message_sources_untouched() {
    runner
        .withUserConfiguration(OtherMessages.class)
        .run(
            context ->
                assertThat(
                        context
                            .getBean("otherMessages", ResourceBundleMessageSource.class)
                            .getBasenameSet())
                    .containsExactly("i18n/extra"));
  }

  @Configuration(proxyBeanMethods = false)
  static class ReloadableMessages {
    @Bean
    MessageSource messageSource() {
      var messages = new ReloadableResourceBundleMessageSource();
      messages.setBasename("classpath:i18n/messages");
      messages.setFallbackToSystemLocale(false);
      return messages;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class StaticMessages {
    @Bean
    MessageSource messageSource() {
      return new StaticMessageSource();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OtherMessages {
    @Bean
    ResourceBundleMessageSource otherMessages() {
      var messages = new ResourceBundleMessageSource();
      messages.setBasename("i18n/extra");
      return messages;
    }
  }
}
