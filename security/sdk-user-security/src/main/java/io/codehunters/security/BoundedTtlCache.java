package io.codehunters.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Size-capped LRU cache whose entries expire a fixed time after they are written. */
final class BoundedTtlCache<V> {

  private final Duration ttl;
  private final Clock clock;
  private final Map<String, Entry<V>> entries;

  BoundedTtlCache(int maxEntries, Duration ttl, Clock clock) {
    this.ttl = ttl;
    this.clock = clock;
    this.entries =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Entry<V>> eldest) {
            return size() > maxEntries;
          }
        };
  }

  synchronized Optional<V> get(String key) {
    Entry<V> entry = entries.get(key);
    if (entry == null || !clock.instant().isBefore(entry.expiresAt())) {
      entries.remove(key);
      return Optional.empty();
    }
    return Optional.of(entry.value());
  }

  synchronized void put(String key, V value) {
    entries.put(key, new Entry<>(value, clock.instant().plus(ttl)));
  }

  synchronized int size() {
    return entries.size();
  }

  private record Entry<V>(V value, Instant expiresAt) {}
}
