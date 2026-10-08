package io.codehunters.geo.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.geo.GeoFenceValidator;
import io.codehunters.geo.polygon.GeoZoneSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Runs a real {@link SpringApplication}, so the legacy-key post-processor is applied. */
class GeoLegacyStartupTest {

  @Test
  void should_disable_the_sdk_when_the_0x_key_turns_it_off() {
    try (var context =
        start("--geo.validation.enabled=false", "--codehunters.geo.adapter=polygon")) {
      assertThat(context.getBeansOfType(GeoFenceValidator.class)).isEmpty();
    }
  }

  @Test
  void should_keep_the_sdk_on_when_the_new_key_overrides_the_0x_key() {
    try (var context =
        start(
            "--geo.validation.enabled=false",
            "--codehunters.geo.enabled=true",
            "--codehunters.geo.adapter=polygon")) {
      assertThat(context.getBeansOfType(GeoFenceValidator.class)).hasSize(1);
    }
  }

  private static ConfigurableApplicationContext start(String... args) {
    var application = new SpringApplication(GeoApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    return application.run(args);
  }

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration(GeoAutoConfiguration.class)
  static class GeoApplication {
    @Bean
    GeoZoneSource zoneSource() {
      return scope -> List.of();
    }
  }
}
