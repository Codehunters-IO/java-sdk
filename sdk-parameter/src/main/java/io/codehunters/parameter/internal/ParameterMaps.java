package io.codehunters.parameter.internal;

import io.codehunters.parameter.Parameter;
import io.codehunters.parameter.ParameterException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Builds the name-keyed views returned by {@code asMap} and {@code asMapByName}. */
public final class ParameterMaps {

  private ParameterMaps() {}

  /**
   * Indexes rows by name, keeping their order and {@code null} values.
   *
   * @param rows the rows of one module (and optionally one submodule)
   * @param value extracts the map value from a row
   * @param <V> the map value type
   * @return an unmodifiable map from name to value
   * @throws ParameterException if a name appears in more than one row
   */
  public static <V> Map<String, V> byName(List<Parameter> rows, Function<Parameter, V> value) {
    Map<String, V> result = new LinkedHashMap<>();
    rows.forEach(row -> putUnique(result, row.name(), value.apply(row)));
    return Collections.unmodifiableMap(result);
  }

  private static <V> void putUnique(Map<String, V> result, String name, V value) {
    if (result.containsKey(name)) {
      throw new ParameterException(
          "Parameter name appears in more than one submodule; narrow by submodule: " + name);
    }
    result.put(name, value);
  }
}
