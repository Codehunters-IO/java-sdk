package io.codehunters.web.device.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.codehunters.web.device.useragent.UserAgentInfo;
import io.codehunters.web.device.useragent.UserAgentParser;
import java.lang.System.Logger.Level;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Caches parsed headers in a bounded Caffeine cache that records hit and miss statistics. Lookups
 * take no global lock. Concurrent callers of the same header share one parse; callers of other
 * headers are never held up by it. A delegate that throws an exception answers {@link
 * UserAgentInfo#UNKNOWN} to the caller and to the callers waiting on it, and nothing is cached, so
 * the next caller of the header parses it again: a failure may be transient. A delegate that throws
 * an {@link Error} releases the callers waiting on it with {@code UNKNOWN}, is not cached either,
 * and the error reaches the caller that triggered the parse.
 */
public final class CachingUserAgentParser implements UserAgentParser {

  private static final System.Logger LOG = System.getLogger(CachingUserAgentParser.class.getName());

  private final UserAgentParser delegate;
  private final AsyncCache<String, UserAgentInfo> entries;

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
    this.entries =
        maxEntries == 0
            ? null
            : Caffeine.newBuilder().maximumSize(maxEntries).recordStats().buildAsync();
  }

  /**
   * The cache, for metrics and diagnostics.
   *
   * @return a view of the cache, empty when caching is disabled
   */
  public Optional<Cache<String, UserAgentInfo>> cache() {
    return Optional.ofNullable(entries).map(AsyncCache::synchronous);
  }

  @Override
  public UserAgentInfo parse(String userAgent) {
    if (userAgent == null) {
      return UserAgentInfo.UNKNOWN;
    }
    if (entries == null) {
      return parseSafely(userAgent);
    }
    var claim = new CompletableFuture<UserAgentInfo>();
    var shared = entries.get(userAgent, (key, executor) -> claim);
    return shared == claim ? fulfil(userAgent, claim) : shared.join();
  }

  // A failed parse (an exception, or a StackOverflowError in the regex engine) must not leave the
  // claim pending, or waiters and every later caller of the header would block forever; nor stay
  // cached, or one transient failure would describe that header as unknown until evicted.
  private UserAgentInfo fulfil(String userAgent, CompletableFuture<UserAgentInfo> claim) {
    try {
      var info = delegate.parse(userAgent);
      claim.complete(info);
      return info;
    } catch (RuntimeException e) {
      release(userAgent, claim);
      return unknownAfter(e);
    } catch (Throwable e) {
      release(userAgent, claim);
      throw e;
    }
  }

  private void release(String userAgent, CompletableFuture<UserAgentInfo> claim) {
    entries.asMap().remove(userAgent, claim);
    claim.complete(UserAgentInfo.UNKNOWN);
  }

  private UserAgentInfo parseSafely(String userAgent) {
    try {
      return delegate.parse(userAgent);
    } catch (RuntimeException e) {
      return unknownAfter(e);
    }
  }

  private static UserAgentInfo unknownAfter(RuntimeException e) {
    LOG.log(Level.WARNING, "User-Agent parser failed: {0}", e.getClass().getName());
    return UserAgentInfo.UNKNOWN;
  }
}
