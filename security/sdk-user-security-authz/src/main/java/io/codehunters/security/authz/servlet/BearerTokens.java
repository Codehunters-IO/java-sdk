package io.codehunters.security.authz.servlet;

import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

/**
 * Finds the token Spring Security validated. Keycloak's enforcer trusts the token it is given as a
 * requesting party token and grants from its permissions without checking its signature, so it is
 * only ever given a token an authentication holds, never one read again from the request.
 */
final class BearerTokens {

  // RFC 6750 b64token: anything else could not have come from a bearer authorization header.
  private static final Pattern BEARER_CHARSET = Pattern.compile("[A-Za-z0-9\\-._~+/]+=*");

  private BearerTokens() {}

  /**
   * The validated token of a bearer-token authentication: Spring Security's own token
   * authentications and sdk-user-security's {@code KeycloakAuthenticationToken} keep it after
   * credentials are erased. Any other authentication has none.
   */
  static Optional<String> of(Authentication authentication) {
    return authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> token
        ? Optional.of(token.getToken().getTokenValue())
        : Optional.empty();
  }

  /** Whether the token could be sent in a bearer authorization header as it is. */
  static boolean isBearerCharset(String token) {
    return BEARER_CHARSET.matcher(token).matches();
  }
}
