package io.codehunters.jpa.converter;

import jakarta.persistence.Converter;

/** Stores a {@code List<String>} as a JSON array. Apply it with {@code @Convert}. */
@Converter
public class StringListConverter extends JsonListConverter<String> {

  /** Creates the converter with the shared default mapper. */
  public StringListConverter() {
    super(String.class);
  }
}
