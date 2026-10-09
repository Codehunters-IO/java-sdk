package io.codehunters.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BoundedTtlCacheTest {

  private static final Duration TTL = Duration.ofSeconds(30);

  private final MutableClock clock = new MutableClock();

  @Test
  void should_return_value_when_entry_is_fresh() {
    var cache = new BoundedTtlCache<Boolean>(10, TTL, clock);
    cache.put("a", true);

    assertThat(cache.get("a")).contains(true);
  }

  @Test
  void should_forget_entry_when_ttl_elapsed() {
    var cache = new BoundedTtlCache<Boolean>(10, TTL, clock);
    cache.put("a", true);

    clock.advance(TTL);

    assertThat(cache.get("a")).isEmpty();
  }

  @Test
  void should_evict_least_recently_used_when_full() {
    var cache = new BoundedTtlCache<Boolean>(2, TTL, clock);
    cache.put("a", true);
    cache.put("b", true);
    cache.get("a");

    cache.put("c", true);

    assertThat(cache.get("b")).isEmpty();
    assertThat(cache.get("a")).contains(true);
    assertThat(cache.get("c")).contains(true);
  }

  @Test
  void should_never_exceed_max_entries_when_all_entries_are_live() {
    var cache = new BoundedTtlCache<Boolean>(2, TTL, clock);
    cache.put("a", true);
    cache.put("b", true);
    cache.put("c", true);
    cache.put("d", true);

    assertThat(cache.size()).isEqualTo(2);
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-05T12:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }
  }
}
