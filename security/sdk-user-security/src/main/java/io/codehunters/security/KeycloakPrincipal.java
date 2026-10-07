package io.codehunters.security;

/**
 * Immutable identity of the current user as established by the gateway and consumed downstream
 * through {@link KeycloakSecurityUtils}.
 *
 * @param userId the user identifier
 * @param username the username
 * @param country the gateway-resolved country code
 * @param city the gateway-resolved city
 * @param latitude the gateway-resolved latitude
 * @param longitude the gateway-resolved longitude
 * @param ip the originating IP address
 * @param userAgent the originating client User-Agent
 * @param sessionId the Keycloak session id ({@code sid} claim) forwarded by the gateway
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
    String sessionId) {}
