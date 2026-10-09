package io.codehunters.parameter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable parameter row returned by the SDK. Includes typed accessors that coerce the raw string
 * {@code value} into native Java types based on the declared {@link ParameterType}.
 *
 * <p>JSON values are exposed as the raw string through {@link #value()}; consumers parse them with
 * their preferred JSON mapper. This type holds no JSON binding to keep it framework-free.
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
   * @return the raw value, {@code null} when the row holds none
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
   *     ParameterType#INTEGER}, or the value is missing, unparseable or out of range
   */
  public long asLong() {
    String raw = requiredValue(ParameterType.INTEGER);
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException ex) {
      throw unreadable(ParameterType.INTEGER);
    }
  }

  /**
   * Returns the value as an int.
   *
   * @return the parsed int value
   * @throws ParameterTypeMismatchException if the declared type is not {@link
   *     ParameterType#INTEGER}, or the value is missing, unparseable or out of {@code int} range
   */
  public int asInteger() {
    long parsed = asLong();
    if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
      throw unreadable(ParameterType.INTEGER);
    }
    return (int) parsed;
  }

  /**
   * Returns the value as a {@link BigDecimal}.
   *
   * @return the parsed decimal value
   * @throws ParameterTypeMismatchException if the declared type is not {@link
   *     ParameterType#DECIMAL}, or the value is missing or unparseable
   */
  public BigDecimal asBigDecimal() {
    String raw = requiredValue(ParameterType.DECIMAL);
    try {
      return new BigDecimal(raw);
    } catch (NumberFormatException ex) {
      throw unreadable(ParameterType.DECIMAL);
    }
  }

  /**
   * Returns the value as a boolean. Only {@code true} and {@code false} are accepted, ignoring case
   * and surrounding spaces.
   *
   * @return the parsed boolean value
   * @throws ParameterTypeMismatchException if the declared type is not {@link
   *     ParameterType#BOOLEAN}, or the value is missing or neither {@code true} nor {@code false}
   */
  public boolean asBoolean() {
    String raw = requiredValue(ParameterType.BOOLEAN);
    if (raw.equalsIgnoreCase("true")) {
      return true;
    }
    if (raw.equalsIgnoreCase("false")) {
      return false;
    }
    throw unreadable(ParameterType.BOOLEAN);
  }

  private String requiredValue(ParameterType expected) {
    ensureType(expected);
    if (value == null) {
      throw unreadable(expected);
    }
    return value.trim();
  }

  private ParameterTypeMismatchException unreadable(ParameterType type) {
    return new ParameterTypeMismatchException(type, name);
  }

  private void ensureType(ParameterType expected) {
    if (this.type != expected) {
      throw new ParameterTypeMismatchException(expected, this.type);
    }
  }
}
