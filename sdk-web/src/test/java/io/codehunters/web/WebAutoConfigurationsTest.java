package io.codehunters.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.device.config.DeviceAutoConfiguration;
import io.codehunters.device.ip.ClientIpResolver;
import io.codehunters.i18n.config.I18nAutoConfiguration;
import io.codehunters.weberrors.config.WebErrorsAutoConfiguration;
import io.codehunters.weberrors.web.WebErrorsExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.LocaleResolver;

/** sdk-web ships three auto-configurations; each must still load and switch off on its own. */
class WebAutoConfigurationsTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  WebErrorsAutoConfiguration.class,
                  DeviceAutoConfiguration.class,
                  I18nAutoConfiguration.class));

  @Test
  void should_register_the_three_auto_configurations() {
    var candidates =
        ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates();

    assertThat(candidates)
        .contains(
            WebErrorsAutoConfiguration.class.getName(),
            DeviceAutoConfiguration.class.getName(),
            I18nAutoConfiguration.class.getName());
  }

  @Test
  void should_load_every_part_together() {
    contextRunner.run(
        context ->
            assertThat(context)
                .hasSingleBean(WebErrorsExceptionHandler.class)
                .hasSingleBean(ClientIpResolver.class)
                .hasBean("localeResolver"));
  }

  @Test
  void should_keep_device_and_i18n_when_web_errors_is_disabled() {
    contextRunner
        .withPropertyValues("codehunters.web-errors.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .doesNotHaveBean(WebErrorsExceptionHandler.class)
                    .hasSingleBean(ClientIpResolver.class)
                    .hasSingleBean(LocaleResolver.class));
  }

  @Test
  void should_keep_web_errors_and_i18n_when_device_is_disabled() {
    contextRunner
        .withPropertyValues("codehunters.device.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(WebErrorsExceptionHandler.class)
                    .doesNotHaveBean(ClientIpResolver.class)
                    .hasSingleBean(LocaleResolver.class));
  }

  @Test
  void should_keep_web_errors_and_device_when_i18n_is_disabled() {
    contextRunner
        .withPropertyValues("codehunters.i18n.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(WebErrorsExceptionHandler.class)
                    .hasSingleBean(ClientIpResolver.class)
                    .doesNotHaveBean(LocaleResolver.class));
  }
}
