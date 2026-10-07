package io.codehunters.jpa.converter;

import jakarta.persistence.Converter;

/** Stores a {@code Map<String, String>} as a JSON object. Apply it with {@code @Convert}. */
@Converter
public class StringMapConverter extends JsonMapConverter<String> {

  /** Creates the converter with the shared default mapper. */
  public StringMapConverter() {
    super(String.class);
  }
}
