package io.codehunters.security.authz.servlet;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.keycloak.adapters.authorization.util.JsonUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

/**
 * Finds the token Spring Security validated. Keycloak's enforcer trusts the token it is given as a
 * requesting party token and grants from its permissions without checking its signature, so it is
 * only ever given a token an authentication holds, never one read again from the request.
 */
final class BearerTokens {

  // RFC 6750 b64token: anything else could not have come from a bearer authorization header.
  private static final String PERMISSIONS = "authorization";
  private static final String AUDIENCE = "aud";
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

  /**
   * Whether Keycloak's enforcer can read the token: it parses every token as a JWT, so an opaque
   * one (from token introspection, for example) would fail inside the enforcer.
   */
  static boolean isJwt(String token) {
    try {
      return JsonUtils.asAccessToken(token) != null;
    } catch (RuntimeException e) {
      return false;
    }
  }

  /**
   * Whether a token carrying permissions was issued to the resource server: Keycloak grants from a
   * token's {@code authorization} claim without asking the realm, and the realm's own requesting
   * party tokens name the resource server in their audience. A token without permissions passes.
   */
  static boolean isIssuedTo(Authentication authentication, String resource) {
    var attributes =
        ((AbstractOAuth2TokenAuthenticationToken<?>) authentication).getTokenAttributes();
    return !attributes.containsKey(PERMISSIONS)
        || resource != null && audience(attributes.get(AUDIENCE)).contains(resource);
  }

  private static Collection<?> audience(Object claim) {
    if (claim instanceof Collection<?> values) {
      return values;
    }
    return claim == null ? List.of() : List.of(claim);
  }
}
