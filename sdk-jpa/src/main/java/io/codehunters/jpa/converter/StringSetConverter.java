package io.codehunters.jpa.converter;

import jakarta.persistence.Converter;

/** Stores a {@code Set<String>} as a JSON array. Apply it with {@code @Convert}. */
@Converter
public class StringSetConverter extends JsonSetConverter<String> {

  /** Creates the converter with the shared default mapper. */
  public StringSetConverter() {
    super(String.class);
  }
}
