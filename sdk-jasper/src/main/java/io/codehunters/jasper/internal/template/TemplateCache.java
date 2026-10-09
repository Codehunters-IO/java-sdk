package io.codehunters.jasper.internal.template;

import io.codehunters.jasper.ReportException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Keeps loaded templates in a bounded least-recently-used map. Concurrent callers of the same
 * template share one load, so a {@code .jrxml} is compiled once however many requests arrive
 * together. A failed load is not cached: its callers get the failure and the next call loads again.
 */
public final class TemplateCache<T> {

  private final int maxEntries;
  private final Function<String, T> loader;
  private final Map<String, CompletableFuture<T>> entries;

  public TemplateCache(int maxEntries, Function<String, T> loader) {
    if (maxEntries < 0) {
      throw new IllegalArgumentException("cache size must not be negative");
    }
    this.maxEntries = maxEntries;
    this.loader = loader;
    this.entries = new LinkedHashMap<>(16, 0.75f, true);
  }

  public T get(String name) {
    if (maxEntries == 0) {
      return loader.apply(name);
    }
    var claim = new CompletableFuture<T>();
    var existing = claimOrGet(name, claim);
    return existing == null ? fulfil(name, claim) : await(existing);
  }

  // Any Throwable releases the waiters and frees the slot: a pending claim would block every later
  // caller of the same template.
  private T fulfil(String name, CompletableFuture<T> claim) {
    try {
      T loaded = loader.apply(name);
      claim.complete(loaded);
      return loaded;
    } catch (Throwable e) {
      synchronized (entries) {
        entries.remove(name, claim);
      }
      claim.completeExceptionally(e);
      throw e;
    }
  }

  private CompletableFuture<T> claimOrGet(String name, CompletableFuture<T> claim) {
    synchronized (entries) {
      var existing = entries.get(name);
      if (existing != null) {
        return existing;
      }
      entries.put(name, claim);
      if (entries.size() > maxEntries) {
        entries.remove(entries.keySet().iterator().next());
      }
      return null;
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
