package io.codehunters.security;

import java.util.Locale;

/** Maps Keycloak role names to Spring Security authorities the same way everywhere. */
final class Authorities {

  private static final String ROLE_PREFIX = "ROLE_";

  private Authorities() {}

  static String fromRole(String role) {
    return ROLE_PREFIX + role.trim().toUpperCase(Locale.ROOT);
  }
}
