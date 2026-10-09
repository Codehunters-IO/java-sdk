package io.codehunters.jasper.internal.template;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.codehunters.jasper.ReportException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Keeps loaded templates in a size-bounded Caffeine cache. Concurrent callers of the same template
 * share one load, run on the first caller's thread, so a {@code .jrxml} is compiled once however
 * many requests arrive together. A failed load is not cached: its callers get the failure and the
 * next call loads again. A size of zero loads on every call. Statistics are recorded for metrics.
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

  public T get(String name) {
    if (!isEnabled()) {
      return loader.apply(name);
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
    try {
      T loaded = loader.apply(name);
      claim.complete(loaded);
      return loaded;
    } catch (Throwable e) {
      claim.completeExceptionally(e);
      throw e;
    }
  }

  private static <T> T await(CompletableFuture<T> pending) {
    try {
      return pending.join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException failure) {
        throw failure;
      }
      throw new ReportException("the template could not be loaded", e.getCause());
    }
  }
}
