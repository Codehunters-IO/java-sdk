package io.codehunters.measure;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class MeasureAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(MeasureAutoConfiguration.class));

  @Test
  @DisplayName("should_register_MeasureAspect_when_MeterRegistry_present")
  void should_register_aspect_with_registry() {
    runner
        .withUserConfiguration(RegistryConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(MeasureAspect.class);
              assertThat(context).hasSingleBean(MeterRegistry.class);
            });
  }

  @Test
  @DisplayName("should_skip_MeasureAspect_when_no_MeterRegistry_present")
  void should_skip_aspect_without_registry() {
    runner.run(context -> assertThat(context).doesNotHaveBean(MeasureAspect.class));
  }

  @Configuration
  static class RegistryConfig {
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
