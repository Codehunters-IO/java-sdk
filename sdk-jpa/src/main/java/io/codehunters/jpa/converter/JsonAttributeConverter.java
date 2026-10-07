package io.codehunters.jpa.converter;

import jakarta.persistence.AttributeConverter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores an attribute as JSON text. Subclass it once per type, keeping the generic type through a
 * {@link TypeReference}:
 *
 * <pre>{@code
 * @Converter
 * public class AddressConverter extends JsonAttributeConverter<Address> {
 *   public AddressConverter() {
 *     super(new TypeReference<>() {});
 *   }
 * }
 * }</pre>
 *
 * <p>{@code null} is stored as a {@code null} column; a {@code null}, blank or {@code null}-literal
 * column is read as {@code null}. Unknown JSON properties are ignored, so removing a field from the
 * type does not break old rows. Without a mapper, a shared default {@link JsonMapper} is used; with
 * Spring Boot, Hibernate creates converters through Spring, so a constructor taking a {@link
 * JsonMapper} receives the application's.
 *
 * @param <T> the attribute type
 */
public abstract class JsonAttributeConverter<T> implements AttributeConverter<T, String> {

  private final JavaType type;
  private final JsonMapper mapper;

  /**
   * Uses the shared default mapper.
   *
   * @param type the attribute type, generics included
   * @throws IllegalArgumentException if {@code type} is null
   */
  protected JsonAttributeConverter(TypeReference<T> type) {
    this(type, DefaultJsonMapper.INSTANCE);
  }

  /**
   * Uses the given mapper.
   *
   * @param type the attribute type, generics included
   * @param mapper the mapper to read and write with
   * @throws IllegalArgumentException if an argument is null
   */
  protected JsonAttributeConverter(TypeReference<T> type, JsonMapper mapper) {
    this(javaType(type, mapper), mapper);
  }

  /**
   * Uses the given Jackson type and mapper.
   *
   * @param type the attribute type
   * @param mapper the mapper to read and write with
   * @throws IllegalArgumentException if an argument is null
   */
  protected JsonAttributeConverter(JavaType type, JsonMapper mapper) {
    if (type == null || mapper == null) {
      throw new IllegalArgumentException("type and mapper must not be null");
    }
    this.type = type;
    this.mapper = mapper;
  }

  @Override
  public String convertToDatabaseColumn(T attribute) {
    if (attribute == null) {
      return null;
    }
    try {
      return mapper.writeValueAsString(attribute);
    } catch (JacksonException e) {
      throw JsonColumnException.write(type, e);
    }
  }

  @Override
  public T convertToEntityAttribute(String column) {
    if (column == null || column.isBlank()) {
      return null;
    }
    try {
      T value = mapper.readValue(column, type);
      return value == null ? null : adapt(value);
    } catch (JacksonException e) {
      throw JsonColumnException.read(type, e);
    }
  }

  /**
   * Adjusts a value read from the column, for example to make a collection unmodifiable.
   *
   * @param value the value Jackson read, never null
   * @return the value given to the entity
   */
  protected T adapt(T value) {
    return value;
  }

  static JsonMapper requireMapper(JsonMapper mapper) {
    if (mapper == null) {
      throw new IllegalArgumentException("mapper must not be null");
    }
    return mapper;
  }

  static void requireType(Object type) {
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
  }

  private static JavaType javaType(TypeReference<?> type, JsonMapper mapper) {
    requireType(type);
    return requireMapper(mapper).getTypeFactory().constructType(type);
  }
}
