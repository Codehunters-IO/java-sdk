package io.codehunters.geo;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class GeoValidationAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(GeoValidationAutoConfiguration.class));

  @Test
  void registers_port_when_datasource_present() {
    runner
        .withUserConfiguration(DataSourceConfig.class)
        .run(ctx -> assertThat(ctx).hasSingleBean(RaffleGeoValidationPort.class));
  }

  @Test
  void skips_port_when_no_datasource() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(RaffleGeoValidationPort.class));
  }

  @Configuration
  static class DataSourceConfig {
    @Bean
    DataSource dataSource() {
      return Mockito.mock(DataSource.class);
    }
  }
}
