package io.codehunters.parameter.domain.model;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Owning module of a parameter, identified by the code stored in the {@code module} column. The
 * constants cover the modules known when this SDK was released; {@link #of(String)} accepts any
 * other code, so a module added on the server does not need a new SDK version.
 *
 * <p>{@link #of(String)} returns the constant itself for a known code, so {@code ==} against a
 * constant keeps working for rows read by the SDK. Compare with {@link #equals(Object)} anyway: a
 * module the SDK does not know is a new instance on every call. Codes are trimmed and compared
 * case-sensitively ({@code "auth"} is not {@link #AUTH}).
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
    code = code.strip();
  }

  /**
   * Returns the module with the given code: the constant itself for a known code, a new module
   * otherwise. Surrounding whitespace is ignored.
   *
   * @param code the module code
   * @return the module
   * @throws IllegalArgumentException if {@code code} is null or blank
   */
  public static ParameterModule of(String code) {
    ParameterModule module = new ParameterModule(code);
    return Known.BY_CODE.getOrDefault(module.code(), module);
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

  /** Lookup of the constants by code, initialised after them on first use. */
  private static final class Known {

    private static final Map<String, ParameterModule> BY_CODE =
        Stream.of(
                GENERAL,
                AUTH,
                CONFIG_SERVER,
                FILE_SHARE,
                MANAGEMENT,
                NOTIFICATIONS,
                PAYMENT,
                RAFFLES)
            .collect(Collectors.toUnmodifiableMap(ParameterModule::code, Function.identity()));
  }
}
