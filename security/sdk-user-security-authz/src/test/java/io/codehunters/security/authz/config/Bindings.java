package io.codehunters.security.authz.config;

import io.codehunters.security.config.KeycloakSecurityProperties;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** Binds properties the way the application context does, from flat {@code key=value} pairs. */
final class Bindings {

  static final Map<String, String> CREDENTIALS =
      Map.of(
          "codehunters.security.authz.enabled", "true",
          "codehunters.security.authz.resource", "svc",
          "codehunters.security.authz.client-secret", "s3cret");

  private Bindings() {}

  static KeycloakPolicyEnforcerProperties authz(Map<String, String> properties) {
    return bind(properties, "codehunters.security.authz", KeycloakPolicyEnforcerProperties.class);
  }

  static KeycloakPolicyEnforcerProperties authzWithCredentials(Map<String, String> properties) {
    Map<String, String> all = new HashMap<>(CREDENTIALS);
    all.putAll(properties);
    return authz(all);
  }

  static KeycloakSecurityProperties security(String url) {
    return bind(
        Map.of(
            "codehunters.security.url", url,
            "codehunters.security.realm", "test",
            "codehunters.security.allow-any-audience", "true",
            "codehunters.security.introspection.enabled", "false"),
        "codehunters.security",
        KeycloakSecurityProperties.class);
  }

  private static <T> T bind(Map<String, String> properties, String prefix, Class<T> type) {
    return new Binder(new MapConfigurationPropertySource(properties)).bindOrCreate(prefix, type);
  }
}
