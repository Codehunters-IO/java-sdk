package io.codehunters.commons.totp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.commons.totp.TotpGenerator;
import io.codehunters.commons.totp.TotpSecret;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/** Runs a real {@link SpringApplication}, so the legacy-key post-processor is applied. */
class TotpLegacyStartupTest {

  private static final TotpSecret SECRET =
      TotpSecret.fromHex("3132333435363738393031323334353637383930");

  @Test
  void should_build_generator_from_legacy_time_step_when_application_starts() {
    try (var context = start("--codehunters.totp.enabled=true", "--vitxo.totp.time-step=60s")) {
      var generator = context.getBean(TotpGenerator.class);

      assertThat(generator.generate(SECRET, Instant.ofEpochSecond(59)))
          .isEqualTo(generator.generate(SECRET, Instant.EPOCH));
    }
  }

  @Test
  void should_fail_startup_when_legacy_time_step_is_not_whole_seconds() {
    assertThatThrownBy(
            () -> start("--codehunters.totp.enabled=true", "--vitxo.totp.time-step=1500ms"))
        .rootCause()
        .hasMessageContaining("timeStep");
  }

  @Test
  void should_not_enable_totp_from_a_leftover_legacy_enabled_key() {
    try (var context = start("--vitxo.totp.enabled=true")) {
      assertThat(context.getBeanNamesForType(TotpGenerator.class)).isEmpty();
    }
  }

  private static ConfigurableApplicationContext start(String... args) {
    var application = new SpringApplication(TotpApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    return application.run(args);
  }

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration(TotpAutoConfiguration.class)
  static class TotpApplication {}
}
