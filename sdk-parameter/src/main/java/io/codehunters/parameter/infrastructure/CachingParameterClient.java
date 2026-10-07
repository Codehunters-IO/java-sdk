package io.codehunters.parameter.infrastructure;

import io.codehunters.parameter.application.ParameterClient;
import io.codehunters.parameter.domain.model.Parameter;
import io.codehunters.parameter.domain.model.ParameterModule;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * {@link ParameterClient} decorator that keeps each answer for a fixed time to live.
 *
 * <ul>
 *   <li>Answers are cached per method and arguments, absent parameters included. Arguments are
 *       normalized first: a blank submodule is the empty submodule, and module or submodule
 *       collections are compared as sorted sets, so order and repeats do not matter.
 *   <li>At most {@value #DEFAULT_MAX_ENTRIES} answers are kept (unless configured otherwise); when
 *       full, the least recently used answer is evicted.
 *   <li>Concurrent misses for the same arguments share one call to the delegate. A failure is
 *       passed to every caller waiting for it and is never cached.
 *   <li>Cached lists are copies, unaffected by later changes to the delegate's lists or to the
 *       caller's collections, and cannot be modified.
 *   <li>{@code asMap} and {@code asMapByName} are built from the cached {@code findByModule}
 *       answer, so all three agree within one time-to-live window.
 * </ul>
 */
public class CachingParameterClient implements ParameterClient {

  /** The maximum number of cached answers when none is given. */
  public static final int DEFAULT_MAX_ENTRIES = 1024;

  private final ParameterClient delegate;
  private final long ttlNanos;
  private final LongSupplier nanoTime;
  private final Map<List<Object>, Entry> entries;
  private final Map<List<Object>, CompletableFuture<Object>> loading = new ConcurrentHashMap<>();

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
    if (maxEntries < 1) {
      throw new IllegalArgumentException("Parameter cache size must be positive: " + maxEntries);
    }
    this.delegate = delegate;
    this.ttlNanos = ttl.toNanos();
    this.nanoTime = nanoTime;
    this.entries = new LruMap(maxEntries);
  }

  @Override
  public Optional<Parameter> lookup(ParameterModule module, String submodule, String name) {
    String normalized = submodule == null ? "" : blankToEmpty(submodule);
    return cached(
        () -> delegate.lookup(module, submodule, name), "lookup", module, normalized, name);
  }

  @Override
  public List<Parameter> findByModule(ParameterModule module, String submodule) {
    String normalized = submodule == null ? null : blankToEmpty(submodule);
    return cached(
        () -> copy(delegate.findByModule(module, submodule)), "findByModule", module, normalized);
  }

  @Override
  public List<Parameter> findByModules(Collection<ParameterModule> modules) {
    Arguments.requireNoNullElements(modules, "modules");
    List<String> codes = sortedDistinct(modules, ParameterModule::code);
    return cached(() -> copy(delegate.findByModules(modules)), "findByModules", codes);
  }

  @Override
  public List<Parameter> findByModuleAndSubmodules(
      ParameterModule module, Collection<String> submodules) {
    Arguments.requireNoNullElements(submodules, "submodules");
    List<String> normalized = sortedDistinct(submodules, CachingParameterClient::blankToEmpty);
    return cached(
        () -> copy(delegate.findByModuleAndSubmodules(module, submodules)),
        "findByModuleAndSubmodules",
        module,
        normalized);
  }

  @Override
  public Map<String, String> asMap(ParameterModule module, String submodule) {
    return ParameterMaps.byName(findByModule(module, submodule), Parameter::value);
  }

  @Override
  public Map<String, Parameter> asMapByName(ParameterModule module, String submodule) {
    return ParameterMaps.byName(findByModule(module, submodule), Function.identity());
  }

  @SuppressWarnings("unchecked")
  private <T> T cached(Supplier<T> load, Object... parts) {
    List<Object> key = Arrays.asList(parts);
    Entry entry = fresh(key);
    if (entry != null) {
      return (T) entry.value();
    }
    CompletableFuture<Object> mine = new CompletableFuture<>();
    CompletableFuture<Object> running = loading.putIfAbsent(key, mine);
    return (T) (running == null ? load(key, load, mine) : await(running));
  }

  private Object load(List<Object> key, Supplier<?> load, CompletableFuture<Object> mine) {
    try {
      Entry entry = fresh(key);
      Object value = entry != null ? entry.value() : store(key, load.get());
      mine.complete(value);
      return value;
    } catch (RuntimeException | Error ex) {
      mine.completeExceptionally(ex);
      throw ex;
    } finally {
      loading.remove(key, mine);
    }
  }

  private static Object await(CompletableFuture<Object> running) {
    try {
      return running.join();
    } catch (CompletionException ex) {
      if (ex.getCause() instanceof Error error) {
        throw error;
      }
      throw ex.getCause() instanceof RuntimeException cause ? cause : ex;
    }
  }

  private Entry fresh(List<Object> key) {
    synchronized (entries) {
      Entry entry = entries.get(key);
      if (entry != null && nanoTime.getAsLong() - entry.loadedAt() >= ttlNanos) {
        entries.remove(key);
        return null;
      }
      return entry;
    }
  }

  private Object store(List<Object> key, Object value) {
    synchronized (entries) {
      entries.put(key, new Entry(value, nanoTime.getAsLong()));
    }
    return value;
  }

  private static List<Parameter> copy(List<Parameter> rows) {
    return Collections.unmodifiableList(new ArrayList<>(rows));
  }

  private static <E> List<String> sortedDistinct(Collection<E> values, Function<E, String> toKey) {
    if (values == null) {
      return null;
    }
    return List.copyOf(
        values.stream().map(toKey).collect(TreeSet::new, TreeSet::add, TreeSet::addAll));
  }

  private static String blankToEmpty(String submodule) {
    return submodule.isBlank() ? "" : submodule;
  }

  private record Entry(Object value, long loadedAt) {}

  /** Access-ordered map that drops its least recently used entry beyond a fixed size. */
  private static final class LruMap extends LinkedHashMap<List<Object>, Entry> {

    private final int maxEntries;

    LruMap(int maxEntries) {
      super(16, 0.75f, true);
      this.maxEntries = maxEntries;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<List<Object>, Entry> eldest) {
      return size() > maxEntries;
    }
  }
}
