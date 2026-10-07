/**
 * User-security SDK: verifies Keycloak access tokens locally, checks the Keycloak session through
 * token introspection, reconciles the gateway's {@code x-user-*} headers with the token, and
 * exposes the {@link io.codehunters.security.KeycloakPrincipal} without depending on Spring
 * Security types.
 */
package io.codehunters.security;
