package io.codehunters.security.authz.servlet;

import io.codehunters.security.KeycloakPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/** Tells token authentications apart and finds the bearer token behind them. */
final class BearerTokens {

  private BearerTokens() {}

  /**
   * Spring Security's own token authentications, and sdk-user-security's, whose principal is a
   * {@link KeycloakPrincipal} and whose credentials (the token) Spring Security erases once the
   * request is authenticated.
   */
  static boolean isTokenBased(Authentication authentication) {
    return authentication instanceof AbstractOAuth2TokenAuthenticationToken<?>
        || authentication.getCredentials() instanceof AbstractOAuth2Token
        || authentication.getPrincipal() instanceof KeycloakPrincipal;
  }

  static Optional<String> of(
      Authentication authentication, HttpServletRequest request, BearerTokenResolver resolver) {
    return held(authentication).or(() -> resolved(request, resolver));
  }

  private static Optional<String> held(Authentication authentication) {
    if (authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> token) {
      return Optional.of(token.getToken().getTokenValue());
    }
    return authentication.getCredentials() instanceof AbstractOAuth2Token token
        ? Optional.of(token.getTokenValue())
        : Optional.empty();
  }

  private static Optional<String> resolved(
      HttpServletRequest request, BearerTokenResolver resolver) {
    try {
      return Optional.ofNullable(resolver.resolve(request)).filter(Predicate.not(String::isBlank));
    } catch (OAuth2AuthenticationException e) {
      return Optional.empty();
    }
  }
}
