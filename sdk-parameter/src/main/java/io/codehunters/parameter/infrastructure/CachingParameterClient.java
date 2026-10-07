package io.codehunters.parameter.infrastructure;

import io.codehunters.parameter.application.ParameterClient;
import io.codehunters.parameter.domain.model.Parameter;
import io.codehunters.parameter.domain.model.ParameterModule;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * {@link ParameterClient} decorator that keeps each answer for a fixed time to live. Every method
 * and argument combination is cached separately, absent parameters included; failures are not
 * cached. Holds at most {@value #DEFAULT_MAX_ENTRIES} entries: when full, expired entries are
 * dropped, and if none has expired the answer is returned without being cached.
 */
public class CachingParameterClient implements ParameterClient {

  /** The maximum number of cached answers when none is given. */
  public static final int DEFAULT_MAX_ENTRIES = 1024;

  private final ParameterClient delegate;
  private final long ttlNanos;
  private final LongSupplier nanoTime;
  private final int maxEntries;
  private final Map<List<Object>, Entry> entries = new ConcurrentHashMap<>();

  /**
   * Creates the cache.
   *
   * @param delegate the client answering cache misses
   * @param ttl how long an answer is kept
   * @throws IllegalArgumentException if {@code ttl} is not positive
   */
  public CachingParameterClient(ParameterClient delegate, Duration ttl) {
    this(delegate, ttl, System::nanoTime, DEFAULT_MAX_ENTRIES);
  }

  CachingParameterClient(
      ParameterClient delegate, Duration ttl, LongSupplier nanoTime, int maxEntries) {
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("Parameter cache TTL must be positive: " + ttl);
    }
    this.delegate = delegate;
    this.ttlNanos = ttl.toNanos();
    this.nanoTime = nanoTime;
    this.maxEntries = maxEntries;
  }

  @Override
  public Optional<Parameter> lookup(ParameterModule module, String submodule, String name) {
    return cached(
        () -> delegate.lookup(module, submodule, name), "lookup", module, submodule, name);
  }

  @Override
  public List<Parameter> findByModule(ParameterModule module, String submodule) {
    return cached(
        () -> delegate.findByModule(module, submodule), "findByModule", module, submodule);
  }

  @Override
  public List<Parameter> findByModules(Collection<ParameterModule> modules) {
    return cached(() -> delegate.findByModules(modules), "findByModules", copy(modules));
  }

  @Override
  public List<Parameter> findByModuleAndSubmodules(
      ParameterModule module, Collection<String> submodules) {
    return cached(
        () -> delegate.findByModuleAndSubmodules(module, submodules),
        "findByModuleAndSubmodules",
        module,
        copy(submodules));
  }

  @Override
  public Map<String, String> asMap(ParameterModule module, String submodule) {
    return cached(() -> delegate.asMap(module, submodule), "asMap", module, submodule);
  }

  @Override
  public Map<String, Parameter> asMapByName(ParameterModule module, String submodule) {
    return cached(() -> delegate.asMapByName(module, submodule), "asMapByName", module, submodule);
  }

  @SuppressWarnings("unchecked")
  private <T> T cached(Supplier<T> load, Object... key) {
    List<Object> cacheKey = Arrays.asList(key);
    long now = nanoTime.getAsLong();
    Entry entry = entries.get(cacheKey);
    if (entry != null && now - entry.loadedAt() < ttlNanos) {
      return (T) entry.value();
    }
    T value = load.get();
    store(cacheKey, new Entry(value, now), now);
    return value;
  }

  private void store(List<Object> key, Entry entry, long now) {
    if (entries.size() >= maxEntries && !entries.containsKey(key)) {
      entries.values().removeIf(cached -> now - cached.loadedAt() >= ttlNanos);
    }
    if (entries.size() < maxEntries || entries.containsKey(key)) {
      entries.put(key, entry);
    }
  }

  private static <E> List<E> copy(Collection<E> values) {
    return values == null ? null : new ArrayList<>(values);
  }

  private record Entry(Object value, long loadedAt) {}
}
