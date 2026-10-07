package io.codehunters.jpa.converter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores a {@link Map} with {@link String} keys as a JSON object, keeping entry order and {@code
 * null} values. Maps read from the column cannot be changed in place: assign a new map so Hibernate
 * sees the change.
 *
 * @param <V> the value type
 */
public abstract class JsonMapConverter<V> extends JsonAttributeConverter<Map<String, V>> {

  /**
   * Uses the shared default mapper.
   *
   * @param valueType the value class
   * @throws IllegalArgumentException if {@code valueType} is null
   */
  protected JsonMapConverter(Class<V> valueType) {
    this(valueType, DefaultJsonMapper.INSTANCE);
  }

  /**
   * Uses the given mapper.
   *
   * @param valueType the value class
   * @param mapper the mapper to read and write with
   * @throws IllegalArgumentException if an argument is null
   */
  protected JsonMapConverter(Class<V> valueType, JsonMapper mapper) {
    super(mapType(valueType, mapper), mapper);
  }

  @Override
  protected Map<String, V> adapt(Map<String, V> value) {
    return Collections.unmodifiableMap(value);
  }

  private static JavaType mapType(Class<?> valueType, JsonMapper mapper) {
    requireType(valueType);
    return requireMapper(mapper)
        .getTypeFactory()
        .constructMapType(LinkedHashMap.class, String.class, valueType);
  }
}
