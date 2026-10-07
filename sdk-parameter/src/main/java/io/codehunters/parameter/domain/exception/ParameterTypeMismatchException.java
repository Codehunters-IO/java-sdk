package io.codehunters.parameter.domain.exception;

import io.codehunters.parameter.domain.model.ParameterType;

/**
 * Thrown when a parameter value is requested as a type incompatible with its declared {@link
 * ParameterType}, or cannot be read as that type. Messages name the parameter but never include its
 * value, which may be a secret.
 */
public class ParameterTypeMismatchException extends ParameterException {

  /**
   * Creates a mismatch exception for an expected vs actual type.
   *
   * @param expected the type the caller requested
   * @param actual the type declared on the parameter, {@code null} when unknown
   */
  public ParameterTypeMismatchException(ParameterType expected, ParameterType actual) {
    super("Parameter type mismatch: expected=" + expected + " actual=" + actual);
  }

  /**
   * Creates a mismatch exception for a value that cannot be read as its declared type.
   *
   * @param type the declared parameter type
   * @param parameterName the name of the parameter whose value failed to parse
   */
  public ParameterTypeMismatchException(ParameterType type, String parameterName) {
    super("Parameter value cannot be read as " + type + ": name=" + parameterName);
  }
}
