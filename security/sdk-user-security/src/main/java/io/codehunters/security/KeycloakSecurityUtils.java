package io.codehunters.security;

import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Static accessors over the current {@link SecurityContextHolder} that expose the {@link
 * KeycloakPrincipal} and its roles without requiring callers to import Spring Security types.
 */
public final class KeycloakSecurityUtils {

  private KeycloakSecurityUtils() {}

  /**
   * Current authenticated principal.
   *
   * @return the {@link KeycloakPrincipal} if one is authenticated, otherwise empty
   */
  public static Optional<KeycloakPrincipal> getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof KeycloakPrincipal principal) {
      return Optional.of(principal);
    }
    return Optional.empty();
  }

  /**
   * Current user ID.
   *
   * @return the user ID, or empty when no user is authenticated
   */
  public static Optional<String> getCurrentUserId() {
    return getCurrentUser().map(KeycloakPrincipal::userId);
  }

  /**
   * Current username.
   *
   * @return the username, or empty when no user is authenticated
   */
  public static Optional<String> getCurrentUsername() {
    return getCurrentUser().map(KeycloakPrincipal::username);
  }

  /**
   * Keycloak session id ({@code sid}) of the current user, forwarded by the gateway.
   *
   * @return the session id, or empty when no user is authenticated or the gateway did not forward
   *     it
   */
  public static Optional<String> getCurrentSessionId() {
    return getCurrentUser().map(KeycloakPrincipal::sessionId);
  }

  /**
   * Country code of the current user, derived from the gateway geolocation headers.
   *
   * @return the country code, or empty when unavailable
   */
  public static Optional<String> getCurrentCountry() {
    return getCurrentUser().map(KeycloakPrincipal::country);
  }

  /**
   * City of the current user, derived from the gateway geolocation headers.
   *
   * @return the city, or empty when unavailable
   */
  public static Optional<String> getCurrentCity() {
    return getCurrentUser().map(KeycloakPrincipal::city);
  }

  /**
   * Latitude of the current user, derived from the gateway geolocation headers.
   *
   * @return the latitude, or empty when unavailable
   */
  public static Optional<String> getCurrentLatitude() {
    return getCurrentUser().map(KeycloakPrincipal::latitude);
  }

  /**
   * Longitude of the current user, derived from the gateway geolocation headers.
   *
   * @return the longitude, or empty when unavailable
   */
  public static Optional<String> getCurrentLongitude() {
    return getCurrentUser().map(KeycloakPrincipal::longitude);
  }

  /**
   * IP address of the current user, derived from the gateway geolocation headers.
   *
   * @return the IP address, or empty when unavailable
   */
  public static Optional<String> getCurrentIp() {
    return getCurrentUser().map(KeycloakPrincipal::ip);
  }

  /**
   * Roles granted to the current authentication, with the {@code ROLE_} prefix preserved.
   *
   * @return the granted role names; empty when no user is authenticated
   */
  public static List<String> getCurrentRoles() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return List.of();
    }
    return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
  }

  /**
   * Checks whether the current user has the given role. The argument may include the {@code ROLE_}
   * prefix or omit it.
   *
   * @param role the role name to check
   * @return {@code true} if the current user has the role
   */
  public static boolean hasRole(String role) {
    String prefixed = role.startsWith("ROLE_") ? role : "ROLE_" + role;
    return getCurrentRoles().contains(prefixed);
  }
}
