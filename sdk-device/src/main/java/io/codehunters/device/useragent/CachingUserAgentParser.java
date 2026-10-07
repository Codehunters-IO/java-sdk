package io.codehunters.device.useragent;

import java.lang.System.Logger.Level;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Caches parsed headers in a bounded least-recently-used map. Concurrent callers of the same header
 * share one parse. A failing delegate answers {@link UserAgentInfo#UNKNOWN}, which is cached like
 * any other answer because parsing is deterministic.
 */
public final class CachingUserAgentParser implements UserAgentParser {

  private static final System.Logger LOG = System.getLogger(CachingUserAgentParser.class.getName());

  private final UserAgentParser delegate;
  private final int maxEntries;
  private final Map<String, CompletableFuture<UserAgentInfo>> entries;

  /**
   * Creates the cache.
   *
   * @param delegate the parser doing the work
   * @param maxEntries the number of headers kept; 0 disables caching
   */
  public CachingUserAgentParser(UserAgentParser delegate, int maxEntries) {
    if (delegate == null) {
      throw new IllegalArgumentException("delegate must not be null");
    }
    if (maxEntries < 0) {
      throw new IllegalArgumentException("cache size must not be negative");
    }
    this.delegate = delegate;
    this.maxEntries = maxEntries;
    this.entries = new LinkedHashMap<>(16, 0.75f, true);
  }

  @Override
  public UserAgentInfo parse(String userAgent) {
    if (userAgent == null) {
      return UserAgentInfo.UNKNOWN;
    }
    if (maxEntries == 0) {
      return parseSafely(userAgent);
    }
    var claim = new CompletableFuture<UserAgentInfo>();
    var existing = claimOrGet(userAgent, claim);
    if (existing != null) {
      return existing.join();
    }
    claim.complete(parseSafely(userAgent));
    return claim.join();
  }

  private CompletableFuture<UserAgentInfo> claimOrGet(
      String userAgent, CompletableFuture<UserAgentInfo> claim) {
    synchronized (entries) {
      var existing = entries.get(userAgent);
      if (existing == null) {
        entries.put(userAgent, claim);
        evictBeyondLimit();
      }
      return existing;
    }
  }

  private void evictBeyondLimit() {
    var iterator = entries.keySet().iterator();
    while (entries.size() > maxEntries) {
      iterator.next();
      iterator.remove();
    }
  }

  private UserAgentInfo parseSafely(String userAgent) {
    try {
      return delegate.parse(userAgent);
    } catch (RuntimeException e) {
      LOG.log(Level.WARNING, "User-Agent parser failed: {0}", e.getClass().getName());
      return UserAgentInfo.UNKNOWN;
    }
  }
}
