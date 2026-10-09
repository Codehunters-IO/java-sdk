package io.codehunters.security;

import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Immutable identity of the current user as established by the verified token and consumed
 * downstream through {@link KeycloakSecurityUtils}.
 *
 * <p>Only {@code userId}, {@code username} and {@code sessionId} come from the token. The other
 * components are the gateway's {@link GatewayContext}: client-supplied and not verified.
 *
 * <p>{@link #getName()} is the user id, so {@code Authentication.getName()}, {@code
 * HttpServletRequest.getRemoteUser()} and {@code @PreAuthorize("authentication.name == #id")} see
 * the user id. {@link #toString()} carries the user id only, keeping the gateway context and the
 * session id out of logs and security events.
 *
 * @param userId the user identifier ({@code sub} claim)
 * @param username the username ({@code preferred_username} claim)
 * @param country the gateway-reported country code; not verified
 * @param city the gateway-reported city; not verified
 * @param latitude the gateway-reported latitude; not verified
 * @param longitude the gateway-reported longitude; not verified
 * @param ip the gateway-reported client IP address; not verified
 * @param userAgent the client's {@code User-Agent}; not verified
 * @param sessionId the Keycloak session id ({@code sid} claim)
 */
public record KeycloakPrincipal(
    String userId,
    String username,
    String country,
    String city,
    String latitude,
    String longitude,
    String ip,
    String userAgent,
    String sessionId)
    implements AuthenticatedPrincipal {

  /**
   * The user id, which Spring Security reports as the authentication name.
   *
   * @return the user id
   */
  @Override
  public String getName() {
    return userId;
  }

  /**
   * The client context the gateway forwarded. Client-supplied and not verified: see {@link
   * GatewayContext}.
   *
   * @return the gateway context; its components are {@code null} when the gateway sent no header
   */
  public GatewayContext gatewayContext() {
    return new GatewayContext(country, city, latitude, longitude, ip, userAgent);
  }

  /**
   * Gateway-reported country code.
   *
   * @return the country code, or {@code null}
   * @deprecated client-supplied and not verified; use {@link #gatewayContext()}{@code .country()}.
   */
  @Deprecated(since = "4.1.0", forRemoval = true)
  @Override
  public String country() {
    return country;
  }

  /**
   * Gateway-reported city.
   *
   * @return the city, or {@code null}
   * @deprecated client-supplied and not verified; use {@link #gatewayContext()}{@code .city()}.
   */
  @Deprecated(since = "4.1.0", forRemoval = true)
  @Override
  public String city() {
    return city;
  }

  /**
   * Gateway-reported latitude.
   *
   * @return the latitude, or {@code null}
   * @deprecated client-supplied and not verified; use {@link #gatewayContext()}{@code .latitude()}.
   */
  @Deprecated(since = "4.1.0", forRemoval = true)
  @Override
  public String latitude() {
    return latitude;
  }

  /**
   * Gateway-reported longitude.
   *
   * @return the longitude, or {@code null}
   * @deprecated client-supplied and not verified; use {@link #gatewayContext()}{@code
   *     .longitude()}.
   */
  @Deprecated(since = "4.1.0", forRemoval = true)
  @Override
  public String longitude() {
    return longitude;
  }

  /**
   * Gateway-reported client IP address.
   *
   * @return the IP address, or {@code null}
   * @deprecated client-supplied and not verified; use {@link #gatewayContext()}{@code .ip()}, or
   *     {@code sdk-web}'s {@code ClientContextResolver} for an IP resolved behind trusted proxies.
   */
  @Deprecated(since = "4.1.0", forRemoval = true)
  @Override
  public String ip() {
    return ip;
  }

  /**
   * The client's {@code User-Agent}.
   *
   * @return the User-Agent, or {@code null}
   * @deprecated client-supplied and not verified; use {@link #gatewayContext()}{@code
   *     .userAgent()}.
   */
  @Deprecated(since = "4.1.0", forRemoval = true)
  @Override
  public String userAgent() {
    return userAgent;
  }

  @Override
  public String toString() {
    return "KeycloakPrincipal[userId=" + userId + "]";
  }
}
