package io.codehunters.jpa.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validated name-to-property-path maps shared by the sort and search whitelists. Internal: not part
 * of the SDK's API.
 */
public final class PropertyWhitelist {

  private static final Pattern PROPERTY_PATH =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

  private PropertyWhitelist() {}

  /**
   * Maps each property path to itself.
   *
   * @param properties property paths
   * @return the identity map
   * @throws IllegalArgumentException if the array or one of its names is null
   */
  public static Map<String, String> identity(String... properties) {
    if (properties == null || Arrays.stream(properties).anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("property names must not be null");
    }
    return Arrays.stream(properties)
        .collect(Collectors.toMap(Function.identity(), p -> p, (first, again) -> first));
  }

  /**
   * Validates a whitelist and returns it sorted by name.
   *
   * @param entries API name to entity property path
   * @param kind what the whitelist is for, used in messages
   * @return an unmodifiable copy sorted by API name
   * @throws IllegalArgumentException if the map is empty or an entry is not a property path
   */
  public static NavigableMap<String, String> of(Map<String, String> entries, String kind) {
    if (entries == null || entries.isEmpty()) {
      throw new IllegalArgumentException("the " + kind + " whitelist must not be empty");
    }
    entries.forEach((name, path) -> requireEntry(name, path, kind));
    return Collections.unmodifiableNavigableMap(new TreeMap<>(entries));
  }

  private static void requireEntry(String name, String path, String kind) {
    if (!isPropertyPath(name) || !isPropertyPath(path)) {
      throw new IllegalArgumentException(
          kind + " whitelist entries must be property paths such as customer.name");
    }
  }

  private static boolean isPropertyPath(String value) {
    return value != null && PROPERTY_PATH.matcher(value).matches();
  }
}
