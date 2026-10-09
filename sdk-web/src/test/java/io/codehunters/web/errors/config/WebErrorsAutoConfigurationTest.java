package io.codehunters.web.errors.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.tracing.TraceContextPort;
import io.codehunters.web.errors.servlet.WebErrorsExceptionHandler;
import io.codehunters.web.errors.trace.MdcTraceIdProvider;
import io.codehunters.web.errors.trace.TraceIdProvider;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

class WebErrorsAutoConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  WebErrorsAutoConfiguration.class, WebMvcAutoConfiguration.class));

  @Configuration(proxyBeanMethods = false)
  static class OwnHandler {
    @Bean
    ResponseEntityExceptionHandler ownHandler() {
      return new ResponseEntityExceptionHandler() {};
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnTraceIds {
    @Bean
    TraceIdProvider traceIdProvider() {
      return () -> Optional.of("own");
    }
  }

  @Test
  void should_register_the_handler_in_a_servlet_application() {
    runner.run(context -> assertThat(context).hasSingleBean(WebErrorsExceptionHandler.class));
  }

  @Test
  void should_replace_boot_problem_details_handler_when_both_are_enabled() {
    runner
        .withPropertyValues("spring.mvc.problemdetails.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ResponseEntityExceptionHandler.class);
              assertThat(context).hasSingleBean(WebErrorsExceptionHandler.class);
            });
  }

  @Test
  void should_back_off_when_the_application_has_its_own_exception_handler() {
    runner
        .withUserConfiguration(OwnHandler.class)
        .run(context -> assertThat(context).doesNotHaveBean(WebErrorsExceptionHandler.class));
  }

  @Test
  void should_back_off_when_disabled() {
    runner
        .withPropertyValues("codehunters.web.errors.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(WebErrorsExceptionHandler.class));
  }

  @Test
  void should_stay_out_of_applications_that_are_not_servlet_web_applications() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WebErrorsAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(WebErrorsExceptionHandler.class));
  }

  @Test
  void should_keep_the_application_trace_id_provider_when_one_is_declared() {
    runner
        .withUserConfiguration(OwnTraceIds.class)
        .run(
            context ->
                assertThat(context.getBean(TraceIdProvider.class).currentTraceId())
                    .contains("own"));
  }

  @Test
  void should_fail_startup_when_the_type_base_uri_is_relative() {
    runner
        .withPropertyValues("codehunters.web.errors.type-base-uri=errors/")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(
                        "codehunters.web.errors.type-base-uri must be an absolute URI ending"
                            + " with '/'"));
  }

  @Test
  void should_fail_startup_when_the_type_base_uri_does_not_end_with_a_slash() {
    runner
        .withPropertyValues("codehunters.web.errors.type-base-uri=https://errors.example.com")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void should_read_the_trace_id_from_the_mdc_when_the_tracing_sdk_is_absent() {
    runner
        .withClassLoader(new FilteredClassLoader(TraceContextPort.class))
        .run(
            context ->
                assertThat(context.getBean(TraceIdProvider.class))
                    .isInstanceOf(MdcTraceIdProvider.class));
  }
}
