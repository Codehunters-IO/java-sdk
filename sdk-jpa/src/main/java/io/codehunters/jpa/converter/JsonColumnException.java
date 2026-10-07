package io.codehunters.jpa.converter;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;

/**
 * A value could not be written to, or read from, a JSON column. The message names the Java type and
 * where parsing stopped, never the column's content, and the Jackson cause is not kept because its
 * message quotes that content.
 */
public final class JsonColumnException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private JsonColumnException(String message) {
    super(message);
  }

  static JsonColumnException write(JavaType type, JacksonException cause) {
    return new JsonColumnException(
        "Could not write " + type.toCanonical() + " as JSON (" + describe(cause) + ")");
  }

  static JsonColumnException read(JavaType type, JacksonException cause) {
    return new JsonColumnException(
        "Could not read the JSON column as " + type.toCanonical() + " (" + describe(cause) + ")");
  }

  private static String describe(JacksonException cause) {
    var location = cause.getLocation();
    var name = cause.getClass().getSimpleName();
    return location == null
        ? name
        : name + " at line " + location.getLineNr() + ", column " + location.getColumnNr();
  }
}
