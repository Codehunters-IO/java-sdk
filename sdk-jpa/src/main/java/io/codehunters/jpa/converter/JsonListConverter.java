package io.codehunters.jpa.converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores a {@link List} as a JSON array, keeping order and {@code null} elements. Lists read from
 * the column cannot be changed in place: assign a new list so Hibernate sees the change.
 *
 * @param <E> the element type
 */
public abstract class JsonListConverter<E> extends JsonAttributeConverter<List<E>> {

  /**
   * Uses the shared default mapper.
   *
   * @param elementType the element class
   * @throws IllegalArgumentException if {@code elementType} is null
   */
  protected JsonListConverter(Class<E> elementType) {
    this(elementType, DefaultJsonMapper.INSTANCE);
  }

  /**
   * Uses the given mapper.
   *
   * @param elementType the element class
   * @param mapper the mapper to read and write with
   * @throws IllegalArgumentException if an argument is null
   */
  protected JsonListConverter(Class<E> elementType, JsonMapper mapper) {
    super(listType(elementType, mapper), mapper);
  }

  @Override
  protected List<E> adapt(List<E> value) {
    return Collections.unmodifiableList(value);
  }

  private static JavaType listType(Class<?> elementType, JsonMapper mapper) {
    requireType(elementType);
    return requireMapper(mapper)
        .getTypeFactory()
        .constructCollectionType(ArrayList.class, elementType);
  }
}
