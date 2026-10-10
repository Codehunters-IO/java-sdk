package io.codehunters.security.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/** Startup warning for introspection credentials that introspection, being off, never uses. */
final class IntrospectionCredentialsCheck {

  private static final Logger LOG =
      LoggerFactory.getLogger(KeycloakSecurityAutoConfiguration.class);

  private IntrospectionCredentialsCheck() {}

  /** Called only while introspection is off: the bean that runs it is conditional on that. */
  static void warnIfUnused(KeycloakSecurityProperties.Introspection introspection) {
    if (!hasCredentials(introspection)) {
      return;
    }
    LOG.warn(
        "codehunters.security.introspection: introspection credentials configured but"
            + " introspection is disabled — revoked sessions are accepted until token expiry;"
            + " set enabled=true");
  }

  private static boolean hasCredentials(KeycloakSecurityProperties.Introspection introspection) {
    return StringUtils.hasText(introspection.clientId())
        || StringUtils.hasText(introspection.clientSecret());
  }
}
