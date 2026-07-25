package io.codehunters.totp.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.totp.TotpGenerator;
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
        .run(
            context -> {
              assertThat(context).hasSingleBean(TotpGenerator.class);
            });
  }

  @Test
  void should_bind_custom_properties() {
    contextRunner
        .withPropertyValues(
            "vitxo.totp.time-step=60s",
            "vitxo.totp.digits=eight",
            "vitxo.totp.algorithm=hmac-sha256")
        .run(
            context -> {
              assertThat(context).hasSingleBean(TotpGenerator.class);
              var props = context.getBean(TotpProperties.class);
              assertThat(props.timeStep().toSeconds()).isEqualTo(60);
            });
  }
}
