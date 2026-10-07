package io.codehunters.jpa.converter;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** The mapper converters use when none is given: immutable, shared, tolerant of unknown fields. */
final class DefaultJsonMapper {

  static final JsonMapper INSTANCE =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private DefaultJsonMapper() {}
}
