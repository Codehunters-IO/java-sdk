package io.codehunters.messaging.jdbc.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.config.TransportAutoConfigurations;
import org.junit.jupiter.api.Test;

class EventMessagingJdbcAutoConfigurationNameTest {

  @Test
  void should_match_the_name_core_refers_to_when_the_class_is_renamed_or_moved() {
    assertThat(TransportAutoConfigurations.JDBC)
        .isEqualTo(EventMessagingJdbcAutoConfiguration.class.getName());
  }
}
