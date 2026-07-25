package io.codehunters.parameter.domain.exception;

import io.codehunters.parameter.domain.model.ParameterType;

/**
 * Thrown when a parameter value is requested as a type incompatible with its declared {@link
 * ParameterType}.
 */
public class ParameterTypeMismatchException extends ParameterException {

  /**
   * Creates a mismatch exception for an expected vs actual type.
   *
   * @param expected the type the caller requested
   * @param actual the type declared on the parameter
   */
  public ParameterTypeMismatchException(ParameterType expected, ParameterType actual) {
    super("Parameter type mismatch: expected=" + expected + " actual=" + actual);
  }

  /**
   * Creates a mismatch exception describing an unparseable value.
   *
   * @param type the declared parameter type
   * @param value the value that failed to parse
   * @param cause the parsing error
   */
  public ParameterTypeMismatchException(ParameterType type, String value, Throwable cause) {
    super("Parameter value cannot be parsed as " + type + ": value=" + value, cause);
  }
}
