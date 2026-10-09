package io.codehunters.security;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

/**
 * The authentication of a request carrying a verified Keycloak access token: the principal is a
 * {@link KeycloakPrincipal}, the token is the {@link Jwt} Spring Security validated.
 *
 * <p>The token is kept as the authentication's token, not only as its credentials, so it survives
 * Spring Security erasing credentials after authentication. Components that forward the token
 * (Keycloak's policy enforcer, for example) read it from here instead of from the request, where a
 * header or parameter Spring Security never validated could carry another one.
 */
public class KeycloakAuthenticationToken extends AbstractOAuth2TokenAuthenticationToken<Jwt> {

  private static final long serialVersionUID = 1L;

  /**
   * Creates an authenticated token.
   *
   * @param token the validated access token
   * @param principal the user the token names
   * @param authorities the authorities the token grants
   */
  public KeycloakAuthenticationToken(
      Jwt token, KeycloakPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
    super(
        Objects.requireNonNull(token, "token"),
        Objects.requireNonNull(principal, "principal"),
        token,
        authorities);
    setAuthenticated(true);
  }

  @Override
  public Map<String, Object> getTokenAttributes() {
    return getToken().getClaims();
  }

  @Override
  public KeycloakPrincipal getPrincipal() {
    return (KeycloakPrincipal) super.getPrincipal();
  }

  /**
   * The same token and authorities for another principal, keeping the details.
   *
   * @param principal the principal to use
   * @return a new authenticated token
   */
  public KeycloakAuthenticationToken withPrincipal(KeycloakPrincipal principal) {
    var token = new KeycloakAuthenticationToken(getToken(), principal, getAuthorities());
    token.setDetails(getDetails());
    return token;
  }
}
