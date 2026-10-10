/**
 * In-memory, TTL-bounded caching for {@link io.codehunters.parameter.ParameterClient}, backed by
 * Caffeine: the {@link io.codehunters.parameter.cache.CachingParameterClient} decorator, the {@link
 * io.codehunters.parameter.cache.ParameterCache} it keeps answers in (and that applications
 * invalidate), and optional Micrometer metrics.
 */
package io.codehunters.parameter.cache;
