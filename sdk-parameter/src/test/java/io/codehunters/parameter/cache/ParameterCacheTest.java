package io.codehunters.parameter.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.codehunters.parameter.ParameterModule;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
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
  void should_fail_fast_instead_of_deadlocking_when_a_load_reads_its_own_key() {
    var cache = ParameterCache.create(TTL, 4);
    var key = CacheKey.of("lookup", MODULE, "", "LIMIT");

    assertThatThrownBy(
            () ->
                assertTimeoutPreemptively(
                    Duration.ofSeconds(5), () -> cache.get(key, () -> cache.get(key, () -> "x"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("re-entrant load of CacheKey[operation=lookup")
        .hasMessageEndingWith("a parameter load must not read its own answer");
  }

  @Test
  void should_let_a_load_read_another_key_when_the_cache_is_shared() {
    var cache = ParameterCache.create(TTL, 4);
    var outer = CacheKey.of("lookup", MODULE, "", "OUTER");
    var inner = CacheKey.of("lookup", MODULE, "", "INNER");

    String answer = cache.get(outer, () -> cache.get(inner, () -> "inner"));

    assertThat(answer).isEqualTo("inner");
  }

  @Test
  void should_cache_a_null_answer_as_absence_when_the_load_returns_null() {
    var cache = ParameterCache.create(TTL, 4);
    var key = CacheKey.of("lookup", MODULE, "", "MISSING");
    var loads = new AtomicInteger();
    cache.get(key, () -> nullAfterCounting(loads));

    Object second = cache.get(key, () -> nullAfterCounting(loads));

    assertThat(second).isNull();
    assertThat(loads).hasValue(1);
    assertThat(cache.entries().synchronous().stats().loadFailureCount()).isZero();
  }

  private static Object nullAfterCounting(AtomicInteger loads) {
    loads.incrementAndGet();
    return null;
  }

  @Test
  void should_reject_a_null_module_to_invalidate() {
    var cache = ParameterCache.create(TTL, 1);

    assertThatIllegalArgumentException().isThrownBy(() -> cache.invalidate(null));
  }
}
