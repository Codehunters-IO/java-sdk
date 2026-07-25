package io.codehunters.fileshare.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.fileshare.application.FileShareClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FileShareAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(FileShareAutoConfiguration.class));

  @Test
  void creates_client_when_baseUrl_configured() {
    runner
        .withPropertyValues("vitxo.file-share.base-url=http://localhost:9000")
        .run(context -> assertThat(context).hasSingleBean(FileShareClient.class));
  }

  @Test
  void fails_when_baseUrl_missing() {
    runner.run(
        context -> assertThat(context).hasFailed().getFailure().hasMessageContaining("base-url"));
  }

  @Test
  void disabled_by_property() {
    runner
        .withPropertyValues("vitxo.file-share.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(FileShareClient.class));
  }
}
