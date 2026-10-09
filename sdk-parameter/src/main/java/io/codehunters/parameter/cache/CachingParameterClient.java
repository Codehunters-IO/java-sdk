package io.codehunters.parameter.cache;

import io.codehunters.parameter.Parameter;
import io.codehunters.parameter.ParameterClient;
import io.codehunters.parameter.ParameterModule;
import io.codehunters.parameter.internal.Arguments;
import io.codehunters.parameter.internal.ParameterMaps;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link ParameterClient} decorator that keeps each answer in a {@link ParameterCache}.
 *
 * <ul>
 *   <li>Answers are cached per method and arguments, absent parameters included. Arguments are
 *       normalized first: a blank submodule is the empty submodule, and module or submodule
 *       collections are compared as sorted sets, so order and repeats do not matter.
 *   <li>Time to live, size bound, single-flight loading and failure handling are the cache's: see
 *       {@link ParameterCache}.
 *   <li>Cached lists are copies, unaffected by later changes to the delegate's lists or to the
 *       caller's collections, and cannot be modified.
 *   <li>{@code asMap} and {@code asMapByName} are built from the cached {@code findByModule}
 *       answer, so all three agree within one time-to-live window.
 * </ul>
 */
public class CachingParameterClient implements ParameterClient {

  private final ParameterClient delegate;
  private final ParameterCache cache;

  /**
   * Creates the decorator with its own cache of at most {@value ParameterCache#DEFAULT_MAX_ENTRIES}
   * answers.
   *
   * @param delegate the client answering cache misses
   * @param ttl how long an answer is kept
   * @throws IllegalArgumentException if {@code ttl} is not positive
   */
  public CachingParameterClient(ParameterClient delegate, Duration ttl) {
    this(delegate, ParameterCache.create(ttl, ParameterCache.DEFAULT_MAX_ENTRIES));
  }

  /**
   * Creates the decorator over a cache the caller keeps, for example to invalidate it.
   *
   * @param delegate the client answering cache misses
   * @param cache where answers are kept
   * @throws IllegalArgumentException if {@code cache} is {@code null} or disabled
   */
  public CachingParameterClient(ParameterClient delegate, ParameterCache cache) {
    if (cache == null || !cache.isEnabled()) {
      throw new IllegalArgumentException("CachingParameterClient needs an enabled cache");
    }
    this.delegate = delegate;
    this.cache = cache;
  }

  @Override
  public Optional<Parameter> lookup(ParameterModule module, String submodule, String name) {
    String normalized = submodule == null ? "" : blankToEmpty(submodule);
    var key = CacheKey.of("lookup", module, normalized, name);
    return cached(key, () -> delegate.lookup(module, submodule, name));
  }

  @Override
  public List<Parameter> findByModule(ParameterModule module, String submodule) {
    String normalized = submodule == null ? null : blankToEmpty(submodule);
    var key = CacheKey.of("findByModule", module, normalized);
    return cached(key, () -> copy(delegate.findByModule(module, submodule)));
  }

  @Override
  public List<Parameter> findByModules(Collection<ParameterModule> modules) {
    Arguments.requireNoNullElements(modules, "modules");
    var key = CacheKey.ofModules("findByModules", sortedDistinct(modules, ParameterModule::code));
    return cached(key, () -> copy(delegate.findByModules(modules)));
  }

  @Override
  public List<Parameter> findByModuleAndSubmodules(
      ParameterModule module, Collection<String> submodules) {
    Arguments.requireNoNullElements(submodules, "submodules");
    List<String> normalized = sortedDistinct(submodules, CachingParameterClient::blankToEmpty);
    var key = CacheKey.of("findByModuleAndSubmodules", module, normalized);
    return cached(key, () -> copy(delegate.findByModuleAndSubmodules(module, submodules)));
  }

  @Override
  public Map<String, String> asMap(ParameterModule module, String submodule) {
    return ParameterMaps.byName(findByModule(module, submodule), Parameter::value);
  }

  @Override
  public Map<String, Parameter> asMapByName(ParameterModule module, String submodule) {
    return ParameterMaps.byName(findByModule(module, submodule), Function.identity());
  }

  private <T> T cached(CacheKey key, Supplier<T> load) {
    return cache.get(key, load);
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
}
