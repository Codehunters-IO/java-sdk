package io.codehunters.parameter;

/**
 * Owning module of a parameter, identified by the code stored in the {@code module} column. The SDK
 * defines no module codes: each application names its own with {@link #of(String)}, so a new module
 * never needs a new SDK version.
 *
 * <p>Compare modules with {@link #equals(Object)}. Codes are trimmed and compared case-sensitively
 * ({@code of("auth")} is not {@code of("AUTH")}).
 *
 * @param code the module code, as stored in the parameters table
 */
public record ParameterModule(String code) {

  /**
   * Validates the code.
   *
   * @throws IllegalArgumentException if {@code code} is null or blank
   */
  public ParameterModule {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Parameter module code must not be blank");
    }
    code = code.strip();
  }

  /**
   * Returns the module with the given code. Surrounding whitespace is ignored.
   *
   * @param code the module code
   * @return the module
   * @throws IllegalArgumentException if {@code code} is null or blank
   */
  public static ParameterModule of(String code) {
    return new ParameterModule(code);
  }

  /**
   * Returns the module code.
   *
   * @return the code
   */
  @Override
  public String toString() {
    return code;
  }
}
