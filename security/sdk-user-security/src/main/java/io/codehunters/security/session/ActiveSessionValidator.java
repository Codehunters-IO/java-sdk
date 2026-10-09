package io.codehunters.security.session;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Runs the claim validators first and asks Keycloak about the session only for tokens that passed
 * them, so expired or foreign tokens never cost an introspection call.
 *
 * <p>Public only so the auto-configuration in {@code io.codehunters.security.config} can build it;
 * not API, and it may change in any release. Customise session checks through {@link
 * SessionIntrospector} instead.
 */
public final class ActiveSessionValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error INACTIVE_SESSION =
      new OAuth2Error(
          OAuth2ErrorCodes.INVALID_TOKEN, "The Keycloak session is no longer active", null);

  private final OAuth2TokenValidator<Jwt> claimValidator;
  private final SessionIntrospector introspector;

  public ActiveSessionValidator(
      OAuth2TokenValidator<Jwt> claimValidator, SessionIntrospector introspector) {
    this.claimValidator = claimValidator;
    this.introspector = introspector;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    OAuth2TokenValidatorResult claims = claimValidator.validate(jwt);
    if (claims.hasErrors() || introspector.isActive(jwt.getTokenValue())) {
      return claims;
    }
    return OAuth2TokenValidatorResult.failure(INACTIVE_SESSION);
  }
}
