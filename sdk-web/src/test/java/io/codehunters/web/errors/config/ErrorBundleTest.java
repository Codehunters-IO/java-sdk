package io.codehunters.web.errors.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticMessageSource;

/** The SDK's error bundle joins the application's message source, after the application's own. */
@ExtendWith(OutputCaptureExtension.class)
class ErrorBundleTest {

  private static final String UNREACHABLE_WARNING = "sdk-web's error bundle is not reachable";

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

  @Test
  void should_resolve_sdk_messages_when_the_application_has_no_bundle() {
    new WebApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                WebErrorsAutoConfiguration.class, MessageSourceAutoConfiguration.class))
        .run(
            context ->
                assertThat(context.getMessage("error.INVALID_SORT", null, SPANISH))
                    .isEqualTo("El orden solicitado no está permitido."));
  }

  @Test
  void should_keep_boot_message_source_when_the_application_has_a_bundle() {
    runner.run(
        context ->
            assertThat(
                    context
                        .getBean("messageSource", ResourceBundleMessageSource.class)
                        .getBasenameSet())
                .containsExactly("i18n/messages", ErrorBundleRegistrar.BASENAME));
  }

  @Test
  void should_warn_at_startup_when_the_message_source_cannot_reach_the_sdk_bundle(
      CapturedOutput output) {
    runner.withUserConfiguration(StaticMessages.class).run(context -> {});

    assertThat(output).contains(UNREACHABLE_WARNING);
  }

  @Test
  void should_not_warn_at_startup_when_the_message_source_reaches_the_sdk_bundle(
      CapturedOutput output) {
    runner.run(context -> {});

    assertThat(output).doesNotContain(UNREACHABLE_WARNING);
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
