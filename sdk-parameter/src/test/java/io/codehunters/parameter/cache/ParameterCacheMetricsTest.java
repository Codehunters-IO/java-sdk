package io.codehunters.parameter.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.codehunters.parameter.ParameterClient;
import io.codehunters.parameter.ParameterModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ParameterCacheMetricsTest {

  private static final ParameterModule MODULE = ParameterModule.of("ORDERS");

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final ParameterClient delegate = mock(ParameterClient.class);
  private final ParameterCache entries = ParameterCache.create(Duration.ofMinutes(5), 16);

  @Test
  void should_count_hits_and_misses_under_the_cache_name_when_bound() {
    new ParameterCacheMetrics(entries).bindTo(registry);
    var client = new CachingParameterClient(delegate, entries);
    when(delegate.lookup(MODULE, "T", "MAX")).thenReturn(Optional.empty());

    client.lookup(MODULE, "T", "MAX");
    client.lookup(MODULE, "T", "MAX");

    assertThat(gets("hit")).isEqualTo(1.0);
    assertThat(gets("miss")).isEqualTo(1.0);
  }

  @Test
  void should_name_the_cache_codehunters_parameter_cache_when_publishing_metrics() {
    assertThat(ParameterCacheMetrics.CACHE_NAME).isEqualTo("codehunters.parameter.cache");
  }

  @Test
  void should_register_nothing_when_the_cache_is_disabled() {
    new ParameterCacheMetrics(ParameterCache.disabled()).bindTo(registry);

    assertThat(registry.getMeters()).isEmpty();
  }

  private double gets(String result) {
    return registry
        .get("cache.gets")
        .tag("cache", ParameterCacheMetrics.CACHE_NAME)
        .tag("result", result)
        .functionCounter()
        .count();
  }
}
