package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AuditPropertiesTest {

  @Test
  void should_default_to_async_publishing_with_bounded_queue() {
    AuditProperties properties = bind(Map.of());

    assertThat(properties.failOnError()).isFalse();
    assertThat(properties.async())
        .isEqualTo(new AuditProperties.Async(true, 2, 4, 500, Duration.ofSeconds(10)));
  }

  @Test
  void should_bind_async_settings() {
    AuditProperties properties =
        bind(
            Map.of(
                "codehunters.audit.async.enabled", "false",
                "codehunters.audit.async.core-pool-size", "1",
                "codehunters.audit.async.max-pool-size", "8",
                "codehunters.audit.async.queue-capacity", "50",
                "codehunters.audit.async.await-termination", "3s"));

    assertThat(properties.async())
        .isEqualTo(new AuditProperties.Async(false, 1, 8, 50, Duration.ofSeconds(3)));
  }

  @Test
  void should_reject_max_pool_size_below_core_pool_size() {
    assertThatThrownBy(
            () ->
                bind(
                    Map.of(
                        "codehunters.audit.async.core-pool-size", "4",
                        "codehunters.audit.async.max-pool-size", "2")))
        .isInstanceOf(BindException.class)
        .rootCause()
        .hasMessageContaining("max-pool-size");
  }

  @Test
  void should_reject_core_pool_size_below_one() {
    assertThatThrownBy(() -> bind(Map.of("codehunters.audit.async.core-pool-size", "0")))
        .isInstanceOf(BindException.class)
        .rootCause()
        .hasMessageContaining("core-pool-size");
  }

  @Test
  void should_reject_negative_queue_capacity() {
    assertThatThrownBy(() -> bind(Map.of("codehunters.audit.async.queue-capacity", "-1")))
        .isInstanceOf(BindException.class)
        .rootCause()
        .hasMessageContaining("queue-capacity");
  }

  @Test
  void should_reject_negative_await_termination() {
    assertThatThrownBy(() -> bind(Map.of("codehunters.audit.async.await-termination", "-1s")))
        .isInstanceOf(BindException.class)
        .rootCause()
        .hasMessageContaining("await-termination");
  }

  private static AuditProperties bind(Map<String, String> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bindOrCreate("codehunters.audit", AuditProperties.class);
  }
}
