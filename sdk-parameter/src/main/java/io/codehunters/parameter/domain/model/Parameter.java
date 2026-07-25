package io.codehunters.parameter.domain.model;

import io.codehunters.parameter.domain.exception.ParameterTypeMismatchException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable parameter row returned by the SDK. Includes typed accessors that coerce the raw string
 * {@code value} into native Java types based on the declared {@link ParameterType}.
 *
 * <p>JSON values are exposed as the raw string through {@link #value()}; consumers parse them with
 * their preferred JSON mapper. The domain layer holds no JSON binding to keep it framework-free.
 *
 * @param id the parameter identifier
 * @param module the owning module
 * @param submodule the optional submodule (empty string when none)
 * @param name the parameter name
 * @param value the raw string value
 * @param type the declared value type
 * @param enabled whether the parameter is active
 * @param description an optional human-readable description
 * @param createdBy the operator that created the row
 * @param createdAt the creation timestamp
 * @param updatedBy the operator that last modified the row
 * @param updatedAt the last modification timestamp
 */
public record Parameter(
    UUID id,
    ParameterModule module,
    String submodule,
    String name,
    String value,
    ParameterType type,
    boolean enabled,
    String description,
    UUID createdBy,
    OffsetDateTime createdAt,
    UUID updatedBy,
    OffsetDateTime updatedAt) {

  /**
   * Returns the value as a string.
   *
   * @return the raw value
   * @throws ParameterTypeMismatchException if the declared type is not {@link ParameterType#STRING}
   */
  public String asString() {
    ensureType(ParameterType.STRING);
    return value;
  }

  /**
   * Returns the value as a long.
   *
   * @return the parsed long value
   * @throws ParameterTypeMismatchException if the declared type is not {@link
   *     ParameterType#INTEGER} or the value cannot be parsed
   */
  public long asLong() {
    ensureType(ParameterType.INTEGER);
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException ex) {
      throw new ParameterTypeMismatchException(ParameterType.INTEGER, value, ex);
    }
  }

  /**
   * Returns the value as an int.
   *
   * @return the parsed int value
   * @throws ParameterTypeMismatchException if the declared type is not {@link
   *     ParameterType#INTEGER} or the value cannot be parsed
   * @throws ArithmeticException if the value overflows {@code int}
   */
  public int asInteger() {
    return Math.toIntExact(asLong());
  }

  /**
   * Returns the value as a {@link BigDecimal}.
   *
   * @return the parsed decimal value
   * @throws ParameterTypeMismatchException if the declared type is not {@link
   *     ParameterType#DECIMAL} or the value cannot be parsed
   */
  public BigDecimal asBigDecimal() {
    ensureType(ParameterType.DECIMAL);
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException ex) {
      throw new ParameterTypeMismatchException(ParameterType.DECIMAL, value, ex);
    }
  }

  /**
   * Returns the value as a boolean.
   *
   * @return the parsed boolean value
   * @throws ParameterTypeMismatchException if the declared type is not {@link
   *     ParameterType#BOOLEAN}
   */
  public boolean asBoolean() {
    ensureType(ParameterType.BOOLEAN);
    return Boolean.parseBoolean(value.trim());
  }

  private void ensureType(ParameterType expected) {
    if (this.type != expected) {
      throw new ParameterTypeMismatchException(expected, this.type);
    }
  }
}
