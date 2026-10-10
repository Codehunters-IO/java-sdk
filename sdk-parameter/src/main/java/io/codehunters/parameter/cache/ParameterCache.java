package io.codehunters.parameter.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.codehunters.parameter.ParameterModule;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * The answers a {@link CachingParameterClient} keeps, backed by Caffeine. Inject it to drop answers
 * after the parameters table changes, instead of waiting for the time to live.
 *
 * <ul>
 *   <li>Each answer is kept for a fixed time to live after it was loaded.
 *   <li>At most a fixed number of answers is kept; beyond it Caffeine evicts the answers least
 *       likely to be used again.
 *   <li>Concurrent misses for the same key share one load, which runs on the first caller's thread.
 *       A failure is passed to every caller waiting for it and is never cached. Waiters block,
 *       without a timeout, until the shared load ends; the load bounds the wait.
 *   <li>A load must not read its own answer from this cache: that call would wait for the load it
 *       is part of. Such a re-entrant call fails with an {@link IllegalStateException} instead of
 *       deadlocking; reading another answer is allowed. Two loads on different threads that wait
 *       for each other's answer are not detected.
 *   <li>A load that returns {@code null} is an answer, not a failure: the absence is cached like
 *       any other answer and does not count as a failed load.
 *   <li>Hit, miss and eviction statistics are recorded; {@link ParameterCacheMetrics} publishes
 *       them to Micrometer.
 * </ul>
 *
 * <p>A {@linkplain #disabled() disabled} cache keeps nothing, and invalidating it does nothing.
 */
public final class ParameterCache {

  /** The maximum number of cached answers when none is given. */
  public static final int DEFAULT_MAX_ENTRIES = 1024;

  private static final Object ABSENT = new Object();

  private final AsyncCache<CacheKey, Object> entries;
  private final boolean enabled;
  private final ThreadLocal<Set<CacheKey>> loading = ThreadLocal.withInitial(HashSet::new);

  private ParameterCache(AsyncCache<CacheKey, Object> entries, boolean enabled) {
    this.entries = entries;
    this.enabled = enabled;
  }

  /**
   * Creates an enabled cache.
   *
   * @param ttl how long an answer is kept after it was loaded
   * @param maxEntries the maximum number of answers kept
   * @return the cache
   * @throws IllegalArgumentException if {@code ttl} is missing or not positive, or {@code
   *     maxEntries} is below one
   */
  public static ParameterCache create(Duration ttl, int maxEntries) {
    return create(ttl, maxEntries, Ticker.systemTicker());
  }

  /**
   * Returns a cache that keeps nothing, for when caching is off.
   *
   * @return a disabled cache
   */
  public static ParameterCache disabled() {
    return new ParameterCache(Caffeine.newBuilder().maximumSize(0).buildAsync(), false);
  }

  static ParameterCache create(Duration ttl, int maxEntries, Ticker ticker) {
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("Parameter cache TTL must be positive: " + ttl);
    }
    if (maxEntries < 1) {
      throw new IllegalArgumentException("Parameter cache size must be positive: " + maxEntries);
    }
    return new ParameterCache(builder(ttl, maxEntries, ticker).buildAsync(), true);
  }

  // Maintenance runs on the calling thread, so eviction is prompt and no pool thread is borrowed.
  private static Caffeine<Object, Object> builder(Duration ttl, int maxEntries, Ticker ticker) {
    return Caffeine.newBuilder()
        .expireAfterWrite(ttl)
        .maximumSize(maxEntries)
        .ticker(ticker)
        .executor(Runnable::run)
        .recordStats();
  }

  /**
   * Returns whether this cache keeps answers.
   *
   * @return {@code false} for a {@linkplain #disabled() disabled} cache
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Drops every answer that reads the given module, including multi-module queries that name it. A
   * load in progress for such an answer still completes for its callers but is not kept.
   *
   * @param module the module whose answers are dropped
   * @throws IllegalArgumentException if {@code module} is {@code null}
   */
  public void invalidate(ParameterModule module) {
    if (module == null) {
      throw new IllegalArgumentException("module must not be null");
    }
    entries.asMap().keySet().removeIf(key -> key.concerns(module));
  }

  /** Drops every answer. */
  public void invalidateAll() {
    entries.synchronous().invalidateAll();
  }

  long size() {
    entries.synchronous().cleanUp();
    return entries.synchronous().estimatedSize();
  }

  AsyncCache<CacheKey, Object> entries() {
    return entries;
  }

  @SuppressWarnings("unchecked")
  <T> T get(CacheKey key, Supplier<T> load) {
    if (loading.get().contains(key)) {
      throw new IllegalStateException(
          "re-entrant load of " + key + ": a parameter load must not read its own answer");
    }
    var mine = new CompletableFuture<Object>();
    var shared = entries.get(key, (ignored, executor) -> mine);
    return (T) unwrap(shared == mine ? load(key, load, mine) : await(shared));
  }

  private Object load(CacheKey key, Supplier<?> load, CompletableFuture<Object> mine) {
    Set<CacheKey> keys = loading.get();
    keys.add(key);
    try {
      Object value = wrap(load.get());
      mine.complete(value);
      return value;
    } catch (RuntimeException | Error ex) {
      mine.completeExceptionally(ex);
      throw ex;
    } finally {
      release(keys, key);
    }
  }

  private void release(Set<CacheKey> keys, CacheKey key) {
    keys.remove(key);
    if (keys.isEmpty()) {
      loading.remove();
    }
  }

  // Caffeine drops a future completed with null and counts it as a failed load, so absence is
  // kept as an explicit marker instead.
  private static Object wrap(Object value) {
    return value == null ? ABSENT : value;
  }

  private static Object unwrap(Object value) {
    return value == ABSENT ? null : value;
  }

  private static Object await(CompletableFuture<Object> shared) {
    try {
      return shared.join();
    } catch (CompletionException ex) {
      if (ex.getCause() instanceof Error error) {
        throw error;
      }
      throw ex.getCause() instanceof RuntimeException cause ? cause : ex;
    }
  }
}
