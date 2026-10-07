package io.codehunters.weberrors.web;

import io.codehunters.weberrors.config.WebErrorsAutoConfiguration;
import io.codehunters.weberrors.web.fixture.SampleController;
import io.codehunters.weberrors.web.fixture.SampleMessages;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** A servlet application with Spring MVC, Jackson, validation and the sample endpoints. */
final class WebMvcFixture {

  private WebMvcFixture() {}

  static WebApplicationContextRunner runner() {
    return new WebApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                WebErrorsAutoConfiguration.class,
                WebMvcAutoConfiguration.class,
                HttpMessageConvertersAutoConfiguration.class,
                JacksonAutoConfiguration.class,
                ValidationAutoConfiguration.class))
        .withUserConfiguration(SampleMessages.class, SampleController.class);
  }

  static MockMvc mvc(WebApplicationContext context) {
    return MockMvcBuilders.webAppContextSetup(context).build();
  }
}
