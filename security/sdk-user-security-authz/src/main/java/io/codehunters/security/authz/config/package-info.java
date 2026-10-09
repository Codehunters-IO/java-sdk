/**
 * Auto-configuration of Keycloak's policy enforcer: the {@code codehunters.security.authz.*}
 * properties, their mapping onto Keycloak's {@code PolicyEnforcerConfig}, and the wiring of {@link
 * io.codehunters.security.authz.servlet.KeycloakPolicyEnforcerFilter} into the Spring Security
 * filter chain.
 */
package io.codehunters.security.authz.config;
