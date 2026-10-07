package io.codehunters.weberrors.web;

import static io.codehunters.weberrors.web.WebMvcFixture.mvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import io.codehunters.tracing.config.TracingAutoConfiguration;
import io.codehunters.weberrors.web.fixture.FixedTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class TraceIdExtensionTest {

  private static final String MDC_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";

  private final WebApplicationContextRunner runner = WebMvcFixture.runner();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void should_add_the_trace_id_from_the_mdc_when_tracing_is_absent() {
    MDC.put("traceId", MDC_TRACE_ID);

    runner.run(
        context ->
            mvc(context)
                .perform(get("/orders/order-42"))
                .andExpect(jsonPath("$.traceId").value(MDC_TRACE_ID)));
  }

  @Test
  void should_omit_the_trace_id_when_no_trace_is_active() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/orders/order-42"))
                .andExpect(jsonPath("$.traceId").doesNotExist()));
  }

  @Test
  void should_take_the_trace_id_from_the_tracing_sdk_when_its_port_is_present() {
    MDC.put("traceId", MDC_TRACE_ID);

    runner
        .withUserConfiguration(FixedTraceContext.class)
        .run(
            context ->
                mvc(context)
                    .perform(get("/orders/order-42"))
                    .andExpect(jsonPath("$.traceId").value(FixedTraceContext.TRACE_ID)));
  }

  @Test
  void should_use_the_tracing_sdk_default_port_when_its_auto_configuration_runs() {
    MDC.put("traceId", MDC_TRACE_ID);

    runner
        .withConfiguration(AutoConfigurations.of(TracingAutoConfiguration.class))
        .run(
            context ->
                mvc(context)
                    .perform(get("/orders/order-42"))
                    .andExpect(jsonPath("$.traceId").value(MDC_TRACE_ID)));
  }

  @Test
  void should_omit_the_trace_id_when_disabled_by_configuration() {
    MDC.put("traceId", MDC_TRACE_ID);

    runner
        .withPropertyValues("codehunters.web-errors.include-trace-id=false")
        .run(
            context ->
                mvc(context)
                    .perform(get("/orders/order-42"))
                    .andExpect(jsonPath("$.traceId").doesNotExist()));
  }

  @Test
  void should_omit_a_blank_trace_id_reported_by_the_tracing_sdk() {
    MDC.put("traceId", " ");

    runner
        .withConfiguration(AutoConfigurations.of(TracingAutoConfiguration.class))
        .run(
            context ->
                mvc(context)
                    .perform(get("/orders/order-42"))
                    .andExpect(jsonPath("$.traceId").doesNotExist()));
  }

  @Test
  void should_keep_the_trace_id_in_logs_when_it_is_left_out_of_the_body(CapturedOutput output) {
    MDC.put("traceId", MDC_TRACE_ID);

    runner
        .withPropertyValues("codehunters.web-errors.include-trace-id=false")
        .run(context -> mvc(context).perform(get("/boom")));

    assertThat(output).contains("traceId=" + MDC_TRACE_ID);
  }
}
