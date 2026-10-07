package io.codehunters.parameter.domain.model;

/**
 * Owning module of a parameter, identified by the code stored in the {@code module} column. The
 * constants cover the modules known when this SDK was released; {@link #of(String)} accepts any
 * other code, so a module added on the server does not need a new SDK version.
 *
 * @param code the module code, as stored in the parameters table
 */
public record ParameterModule(String code) {

  /** The {@code GENERAL} module. */
  public static final ParameterModule GENERAL = new ParameterModule("GENERAL");

  /** The {@code AUTH} module. */
  public static final ParameterModule AUTH = new ParameterModule("AUTH");

  /** The {@code CONFIG_SERVER} module. */
  public static final ParameterModule CONFIG_SERVER = new ParameterModule("CONFIG_SERVER");

  /** The {@code FILE_SHARE} module. */
  public static final ParameterModule FILE_SHARE = new ParameterModule("FILE_SHARE");

  /** The {@code MANAGEMENT} module. */
  public static final ParameterModule MANAGEMENT = new ParameterModule("MANAGEMENT");

  /** The {@code NOTIFICATIONS} module. */
  public static final ParameterModule NOTIFICATIONS = new ParameterModule("NOTIFICATIONS");

  /** The {@code PAYMENT} module. */
  public static final ParameterModule PAYMENT = new ParameterModule("PAYMENT");

  /** The {@code RAFFLES} module. */
  public static final ParameterModule RAFFLES = new ParameterModule("RAFFLES");

  /**
   * Validates the code.
   *
   * @throws IllegalArgumentException if {@code code} is null or blank
   */
  public ParameterModule {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Parameter module code must not be blank");
    }
  }

  /**
   * Returns the module with the given code.
   *
   * @param code the module code
   * @return the module
   * @throws IllegalArgumentException if {@code code} is null or blank
   */
  public static ParameterModule of(String code) {
    return new ParameterModule(code);
  }

  /**
   * Returns the module code, as an enum constant's name was in 1.x.
   *
   * @return the code
   */
  @Override
  public String toString() {
    return code;
  }
}
