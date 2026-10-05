package io.codehunters.security.authz;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the Keycloak policy enforcement point, bound from {@code
 * codehunters.security.authz}. Keycloak's URL and realm come from {@code codehunters.security}.
 *
 * @param enabled whether the PEP is registered; off by default
 * @param enforcementMode how decisions are applied
 * @param resource client id of this service's resource server in Keycloak
 * @param clientSecret secret of that client
 * @param paths this service's catalog of protected paths
 */
@ConfigurationProperties("codehunters.security.authz")
public record KeycloakPolicyEnforcerProperties(
    boolean enabled,
    @DefaultValue("ENFORCING") EnforcementMode enforcementMode,
    String resource,
    String clientSecret,
    @DefaultValue List<PathMapping> paths) {

  private static final String PREFIX = "codehunters.security.authz.";

  /** Requires the client credentials when enabled. */
  public KeycloakPolicyEnforcerProperties {
    require(!enabled || hasText(resource), PREFIX + "resource is required when enabled");
    require(!enabled || hasText(clientSecret), PREFIX + "client-secret is required when enabled");
    paths = List.copyOf(paths);
  }

  @Override
  public String toString() {
    return "KeycloakPolicyEnforcerProperties[enabled=%s, enforcementMode=%s, resource=%s, clientSecret=***, paths=%s]"
        .formatted(enabled, enforcementMode, resource, paths.size());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }

  /** How the PEP applies Keycloak's decisions. */
  public enum EnforcementMode {
    /** Denials and catalog gaps are rejected; Keycloak outages answer 503. */
    ENFORCING,
    /** Decisions are logged but never block the request. */
    PERMISSIVE,
    /** The PEP does nothing. */
    DISABLED
  }

  /**
   * A protected path and the scopes each HTTP method requires.
   *
   * @param path Ant-style pattern, e.g. {@code /api/v1/admin/users/*}
   * @param name Keycloak resource name
   * @param methods HTTP methods allowed on the path and the scopes they need; any other method is
   *     denied
   */
  public record PathMapping(String path, String name, @DefaultValue List<MethodMapping> methods) {

    /** Requires the pattern and the resource name. */
    public PathMapping {
      require(hasText(path), PREFIX + "paths[].path is required");
      require(hasText(name), PREFIX + "paths[].name is required");
      methods = List.copyOf(methods);
    }
  }

  /**
   * Scopes required for one HTTP method.
   *
   * @param method HTTP method, case-insensitive
   * @param scopes Keycloak scopes requested for the resource
   */
  public record MethodMapping(String method, @DefaultValue List<String> scopes) {

    /** Requires the method. */
    public MethodMapping {
      require(hasText(method), PREFIX + "paths[].methods[].method is required");
      scopes = List.copyOf(scopes);
    }
  }
}
