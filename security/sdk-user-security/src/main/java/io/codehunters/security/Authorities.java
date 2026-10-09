package io.codehunters.security;

import java.util.Locale;

/** Maps Keycloak role names to Spring Security authorities the same way everywhere. */
public final class Authorities {

  private static final String ROLE_PREFIX = "ROLE_";

  private Authorities() {}

  /**
   * The authority Spring Security checks for a Keycloak role, e.g. {@code admin} → {@code
   * ROLE_ADMIN}.
   *
   * @param role a Keycloak realm or client role name
   * @return the authority name
   */
  public static String fromRole(String role) {
    return ROLE_PREFIX + role.trim().toUpperCase(Locale.ROOT);
  }
}
