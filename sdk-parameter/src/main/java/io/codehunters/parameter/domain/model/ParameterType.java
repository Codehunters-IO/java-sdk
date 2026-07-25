package io.codehunters.parameter.domain.model;

/**
 * Declared value type for a parameter. Coercion to native types is performed by {@link Parameter}.
 */
public enum ParameterType {
  STRING,
  INTEGER,
  DECIMAL,
  BOOLEAN,
  JSON
}
