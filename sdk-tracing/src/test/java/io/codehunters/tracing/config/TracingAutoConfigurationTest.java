package io.codehunters.tracing.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.tracing.MdcTaskDecorator;
import io.codehunters.tracing.MdcTraceContextAdapter;
import io.codehunters.tracing.TraceContextPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TracingAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TracingAutoConfiguration.class));

  @Test
  @DisplayName("should_register_TraceContextPort_and_MdcTaskDecorator_when_autoconfig_runs")
  void should_register_default_beans() {
    runner.run(
        context -> {
          assertThat(context.getBean(TraceContextPort.class))
              .isInstanceOf(MdcTraceContextAdapter.class);
          assertThat(context).hasSingleBean(MdcTaskDecorator.class);
        });
  }
}
