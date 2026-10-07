package io.codehunters.totp.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.totp.TotpAlgorithm;
import io.codehunters.totp.TotpDigits;
import io.codehunters.totp.TotpGenerator;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TotpAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TotpAutoConfiguration.class));

  @Test
  void should_create_totp_generator_bean() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(TotpGenerator.class);
          assertThat(context).hasSingleBean(TotpProperties.class);
        });
  }

  @Test
  void should_not_create_bean_when_already_defined() {
    contextRunner
        .withBean(TotpGenerator.class, TotpGenerator::new)
        .run(context -> assertThat(context).hasSingleBean(TotpGenerator.class));
  }

  @Test
  void should_bind_codehunters_totp_properties() {
    contextRunner
        .withPropertyValues(
            "codehunters.totp.time-step=60s",
            "codehunters.totp.digits=eight",
            "codehunters.totp.algorithm=hmac-sha256")
        .run(
            context -> {
              var props = context.getBean(TotpProperties.class);
              assertThat(props.timeStep()).isEqualTo(Duration.ofSeconds(60));
              assertThat(props.digits()).isEqualTo(TotpDigits.EIGHT);
              assertThat(props.algorithm()).isEqualTo(TotpAlgorithm.HMAC_SHA256);
            });
  }

  @Test
  void should_apply_defaults_when_no_property_is_set() {
    contextRunner.run(
        context -> {
          var props = context.getBean(TotpProperties.class);
          assertThat(props.timeStep()).isEqualTo(Duration.ofSeconds(30));
          assertThat(props.digits()).isEqualTo(TotpDigits.SIX);
          assertThat(props.algorithm()).isEqualTo(TotpAlgorithm.HMAC_SHA512);
        });
  }

  @Test
  void should_fail_startup_when_time_step_is_not_whole_seconds() {
    contextRunner
        .withPropertyValues("codehunters.totp.time-step=1500ms")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("timeStep"));
  }
}
