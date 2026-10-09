package io.codehunters.web.errors.servlet;

import static io.codehunters.web.errors.servlet.WebMvcFixture.mvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import io.codehunters.web.errors.config.WebErrorsAutoConfiguration;
import io.codehunters.web.errors.servlet.fixture.SampleController;
import io.codehunters.web.errors.servlet.fixture.SampleService;
import io.codehunters.web.errors.servlet.fixture.SpanishJvmLocale;
import io.codehunters.web.i18n.config.I18nAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.http.HttpHeaders;

/** The SDK's own details follow the client's language, with or without application bundles. */
@ExtendWith(SpanishJvmLocale.class)
class SdkBundleLocalizationTest {

  private static final String SORT_EN = "The requested sort is not allowed.";
  private static final String SORT_ES = "El orden solicitado no está permitido.";

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  WebErrorsAutoConfiguration.class,
                  I18nAutoConfiguration.class,
                  MessageSourceAutoConfiguration.class,
                  WebMvcAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  JacksonAutoConfiguration.class,
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(SampleService.class, SampleController.class);

  @Test
  void should_localize_the_sdk_detail_when_the_application_has_no_bundle() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/default-coded-client-input").header("X-Locale", "es"))
                .andExpect(jsonPath("$.code").value(SampleController.DEFAULT_CLIENT_INPUT_CODE))
                .andExpect(jsonPath("$.detail").value(SORT_ES)));
  }

  @Test
  void should_answer_in_english_when_english_is_asked_on_a_spanish_jvm() {
    runner
        .withPropertyValues("spring.messages.basename=i18n/messages")
        .run(
            context ->
                mvc(context)
                    .perform(
                        get("/default-coded-client-input")
                            .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                    .andExpect(jsonPath("$.detail").value(SORT_EN)));
  }
}
