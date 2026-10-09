package io.codehunters.parameter.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;

/**
 * Publishes a {@link ParameterCache}'s statistics through Micrometer's standard cache meters
 * ({@code cache.gets}, {@code cache.puts}, {@code cache.evictions}, {@code cache.size}, ...),
 * tagged {@code cache=}{@value #CACHE_NAME}. A disabled cache registers nothing. The
 * auto-configuration binds it when a single {@link MeterRegistry} exists; without Spring, call
 * {@link #bindTo(MeterRegistry)} yourself. Needs {@code io.micrometer:micrometer-core} on the
 * classpath.
 */
public final class ParameterCacheMetrics implements MeterBinder {

  /** The value of the {@code cache} tag on every meter. */
  public static final String CACHE_NAME = "codehunters.parameter.cache";

  private final ParameterCache cache;

  /**
   * Creates the binder.
   *
   * @param cache the cache whose statistics are published
   */
  public ParameterCacheMetrics(ParameterCache cache) {
    this.cache = cache;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    if (cache.isEnabled()) {
      CaffeineCacheMetrics.monitor(registry, cache.entries(), CACHE_NAME, Tags.empty());
    }
  }
}
