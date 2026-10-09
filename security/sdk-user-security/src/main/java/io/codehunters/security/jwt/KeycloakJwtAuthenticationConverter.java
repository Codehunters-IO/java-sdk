package io.codehunters.security.jwt;

import io.codehunters.security.Authorities;
import io.codehunters.security.KeycloakPrincipal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Turns a verified Keycloak access token into an authentication whose principal is a {@link
 * KeycloakPrincipal} and whose authorities are the realm roles plus the roles of the configured
 * clients, as {@code ROLE_<NAME>}.
 */
public class KeycloakJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final List<String> roleClients;

  /**
   * Creates the converter.
   *
   * @param roleClients clients whose {@code resource_access} roles are granted
   */
  public KeycloakJwtAuthenticationConverter(List<String> roleClients) {
    this.roleClients = List.copyOf(roleClients);
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
      throw new InvalidBearerTokenException("The token has no subject");
    }
    var principal =
        new KeycloakPrincipal(
            jwt.getSubject(),
            jwt.getClaimAsString("preferred_username"),
            null,
            null,
            null,
            null,
            null,
            null,
            jwt.getClaimAsString("sid"));
    return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, authorities(jwt));
  }

  private List<GrantedAuthority> authorities(Jwt jwt) {
    return Stream.concat(roles(jwt.getClaimAsMap("realm_access")), clientRoles(jwt))
        .map(Authorities::fromRole)
        .distinct()
        .<GrantedAuthority>map(SimpleGrantedAuthority::new)
        .toList();
  }

  private Stream<String> clientRoles(Jwt jwt) {
    Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
    if (resourceAccess == null) {
      return Stream.empty();
    }
    return roleClients.stream()
        .map(resourceAccess::get)
        .filter(Map.class::isInstance)
        .flatMap(client -> roles((Map<?, ?>) client));
  }

  private static Stream<String> roles(Map<?, ?> holder) {
    if (holder == null || !(holder.get("roles") instanceof Collection<?> roles)) {
      return Stream.empty();
    }
    return roles.stream().filter(String.class::isInstance).map(String.class::cast);
  }
}
