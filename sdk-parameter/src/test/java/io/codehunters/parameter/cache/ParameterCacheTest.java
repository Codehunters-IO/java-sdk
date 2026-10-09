package io.codehunters.parameter.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.codehunters.parameter.ParameterModule;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ParameterCacheTest {

  private static final Duration TTL = Duration.ofMinutes(5);
  private static final ParameterModule MODULE = ParameterModule.of("ORDERS");

  @Test
  void should_be_enabled_when_created_with_a_ttl() {
    assertThat(ParameterCache.create(TTL, 1).isEnabled()).isTrue();
  }

  @Test
  void should_report_a_disabled_cache_as_disabled() {
    assertThat(ParameterCache.disabled().isEnabled()).isFalse();
  }

  @Test
  void should_ignore_invalidation_when_disabled() {
    var cache = ParameterCache.disabled();

    cache.invalidate(MODULE);
    cache.invalidateAll();

    assertThat(cache.size()).isZero();
  }

  @Test
  void should_reject_a_zero_ttl() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ParameterCache.create(Duration.ZERO, 1))
        .withMessageContaining("TTL must be positive");
  }

  @Test
  void should_reject_a_negative_ttl() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ParameterCache.create(Duration.ofSeconds(-1), 1))
        .withMessageContaining("TTL must be positive");
  }

  @Test
  void should_reject_a_missing_ttl() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ParameterCache.create(null, 1))
        .withMessageContaining("TTL must be positive");
  }

  @Test
  void should_reject_a_size_below_one() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ParameterCache.create(TTL, 0))
        .withMessageContaining("size must be positive");
  }

  @Test
  void should_reject_a_null_module_to_invalidate() {
    var cache = ParameterCache.create(TTL, 1);

    assertThatIllegalArgumentException().isThrownBy(() -> cache.invalidate(null));
  }
}
