package io.codehunters.geo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcGeoFencesTest {

  private final DataSource dataSource = mock(DataSource.class);

  @Test
  void should_apply_the_query_timeout_in_seconds_to_the_template() {
    assertThat(JdbcGeoFences.template(dataSource, Duration.ofSeconds(3)).getQueryTimeout())
        .isEqualTo(3);
  }

  @Test
  void should_round_a_sub_second_timeout_up_to_one_second() {
    assertThat(JdbcGeoFences.template(dataSource, Duration.ofMillis(200)).getQueryTimeout())
        .isEqualTo(1);
  }

  @Test
  void should_round_a_fractional_timeout_up_to_the_next_second() {
    assertThat(JdbcGeoFences.template(dataSource, Duration.ofMillis(2500)).getQueryTimeout())
        .isEqualTo(3);
  }
}
