package io.codehunters.jpa.converter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores a {@link Set} as a JSON array. Reads keep the stored order and drop duplicates; sets read
 * from the column cannot be changed in place: assign a new set so Hibernate sees the change.
 *
 * @param <E> the element type
 */
public abstract class JsonSetConverter<E> extends JsonAttributeConverter<Set<E>> {

  /**
   * Uses the shared default mapper.
   *
   * @param elementType the element class
   * @throws IllegalArgumentException if {@code elementType} is null
   */
  protected JsonSetConverter(Class<E> elementType) {
    this(elementType, DefaultJsonMapper.INSTANCE);
  }

  /**
   * Uses the given mapper.
   *
   * @param elementType the element class
   * @param mapper the mapper to read and write with
   * @throws IllegalArgumentException if an argument is null
   */
  protected JsonSetConverter(Class<E> elementType, JsonMapper mapper) {
    super(setType(elementType, mapper), mapper);
  }

  @Override
  protected Set<E> adapt(Set<E> value) {
    return Collections.unmodifiableSet(value);
  }

  private static JavaType setType(Class<?> elementType, JsonMapper mapper) {
    requireType(elementType);
    return requireMapper(mapper)
        .getTypeFactory()
        .constructCollectionType(LinkedHashSet.class, elementType);
  }
}
