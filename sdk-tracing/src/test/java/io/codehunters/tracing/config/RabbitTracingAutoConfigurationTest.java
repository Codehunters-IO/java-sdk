package io.codehunters.tracing.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.tracing.InboundTraceMethodInterceptor;
import io.codehunters.tracing.RabbitTracingBeanPostProcessor;
import io.codehunters.tracing.TracePropagationMessagePostProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

class RabbitTracingAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(JsonMapperConfig.class)
          .withConfiguration(AutoConfigurations.of(RabbitTracingAutoConfiguration.class));

  @Test
  @DisplayName("should_register_all_rabbit_tracing_beans_when_autoconfig_runs")
  void should_register_all_beans() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(TracePropagationMessagePostProcessor.class);
          assertThat(context).hasSingleBean(InboundTraceMethodInterceptor.class);
          assertThat(context).hasSingleBean(RabbitTracingBeanPostProcessor.class);
        });
  }

  @Configuration
  static class JsonMapperConfig {
    @Bean
    JsonMapper jsonMapper() {
      return JsonMapper.builder().build();
    }
  }
}
