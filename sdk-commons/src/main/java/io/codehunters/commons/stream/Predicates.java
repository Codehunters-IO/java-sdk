package io.codehunters.commons.stream;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/** Predicates the JDK does not provide. */
public final class Predicates {

  private Predicates() {}

  /**
   * Returns a stateful predicate that accepts the first element seen for each key, as extracted by
   * {@code keyExtractor}. {@code null} keys count as one key. The predicate is thread-safe, but in
   * a parallel stream "first" means first to be tested, not first in encounter order. Create a new
   * predicate for every stream.
   *
   * @param keyExtractor the function that extracts the key
   * @param <T> the element type
   * @return the predicate
   */
  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    if (keyExtractor == null) {
      throw new IllegalArgumentException("keyExtractor must not be null");
    }
    Set<Optional<Object>> seen = ConcurrentHashMap.newKeySet();
    return element -> seen.add(Optional.ofNullable(keyExtractor.apply(element)));
  }
}
