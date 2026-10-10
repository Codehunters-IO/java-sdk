package io.codehunters.jasper.internal.template;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.codehunters.jasper.ReportException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Keeps loaded templates in a size-bounded Caffeine cache. Concurrent callers of the same template
 * share one load, run on the first caller's thread, so a {@code .jrxml} is compiled once however
 * many requests arrive together. A failed load is not cached: its callers get the failure and the
 * next call loads again. A size of zero loads on every call. Statistics are recorded for metrics.
 *
 * <ul>
 *   <li>The loader must not read its own template from this cache: that call would wait for the
 *       load it is part of. Such a re-entrant call fails with an {@link IllegalStateException}
 *       instead of deadlocking; reading another template is allowed. Two loads on different threads
 *       that wait for each other's template are not detected.
 *   <li>Waiters block, without a timeout, until the shared load ends; the load bounds the wait.
 *   <li>The loader and its waiters see the same failure: a {@link RuntimeException} or an {@link
 *       Error} is rethrown as is, and only a checked exception, which a loader can throw only by
 *       sneaking it past the compiler, is wrapped in a {@link ReportException}.
 *   <li>A loader that returns {@code null} fails the load with a {@link ReportException}: a
 *       template cannot be absent, so a missing one must be reported by the loader as a failure.
 * </ul>
 */
public final class TemplateCache<T> {

  private final int maxEntries;
  private final Function<String, T> loader;
  private final AsyncCache<String, T> entries;

  public TemplateCache(int maxEntries, Function<String, T> loader) {
    if (maxEntries < 0) {
      throw new IllegalArgumentException("cache size must not be negative");
    }
    this.maxEntries = maxEntries;
    this.loader = loader;
    // Maintenance runs on the calling thread, so eviction is prompt and no pool thread is borrowed.
    this.entries =
        Caffeine.newBuilder()
            .maximumSize(maxEntries)
            .executor(Runnable::run)
            .recordStats()
            .buildAsync();
  }

  private final ThreadLocal<Set<String>> loading = ThreadLocal.withInitial(HashSet::new);

  public T get(String name) {
    if (!isEnabled()) {
      return loader.apply(name);
    }
    if (loading.get().contains(name)) {
      throw new IllegalStateException(
          "re-entrant load of template '"
              + name
              + "': a template loader must not read its own template");
    }
    var claim = new CompletableFuture<T>();
    var shared = entries.get(name, (ignored, executor) -> claim);
    return shared == claim ? fulfil(name, claim) : await(shared);
  }

  public boolean isEnabled() {
    return maxEntries > 0;
  }

  /** The underlying cache, for binding metrics. */
  public AsyncCache<String, T> entries() {
    return entries;
  }

  long size() {
    entries.synchronous().cleanUp();
    return entries.synchronous().estimatedSize();
  }

  // Any Throwable releases the waiters and frees the slot (Caffeine drops a failed future): a
  // pending claim would block every later caller of the same template.
  private T fulfil(String name, CompletableFuture<T> claim) {
    Set<String> mine = loading.get();
    mine.add(name);
    try {
      T loaded = present(name, loader.apply(name));
      claim.complete(loaded);
      return loaded;
    } catch (Throwable e) {
      claim.completeExceptionally(e);
      throw e;
    } finally {
      release(mine, name);
    }
  }

  private void release(Set<String> mine, String name) {
    mine.remove(name);
    if (mine.isEmpty()) {
      loading.remove();
    }
  }

  private static <T> T present(String name, T loaded) {
    if (loaded == null) {
      throw new ReportException("the template loader returned no template for '" + name + "'");
    }
    return loaded;
  }

  private static <T> T await(CompletableFuture<T> pending) {
    try {
      return pending.join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException failure) {
        throw failure;
      }
      if (e.getCause() instanceof Error error) {
        throw error;
      }
      throw new ReportException("the template could not be loaded", e.getCause());
    }
  }
}
