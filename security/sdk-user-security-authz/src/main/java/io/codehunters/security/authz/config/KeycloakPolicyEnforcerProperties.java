package io.codehunters.security.authz.config;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the Keycloak policy enforcer, bound from {@code codehunters.security.authz} and
 * mapped onto Keycloak's {@code PolicyEnforcerConfig}. Keycloak's URL and realm come from {@code
 * codehunters.security}. Field semantics are Keycloak's; see its policy enforcer documentation.
 *
 * @param enabled whether the enforcer is added to the security filter chain; off by default
 * @param enforcementMode how requests to paths without a mapping are treated
 * @param resource client id of this service's resource server in Keycloak
 * @param clientSecret secret of that client
 * @param lazyLoadPaths resolve resources from Keycloak by URI on demand instead of at first use
 * @param httpMethodAsScope use the HTTP method as the scope of methods the catalog does not list
 * @param pathCache cache of resolved paths
 * @param claimInformationPoint claim information point applied to every path
 * @param paths this service's catalog of protected paths
 * @param connectTimeout connect timeout of calls to Keycloak
 * @param readTimeout read timeout of calls to Keycloak
 * @param maxConnections upper bound of concurrent connections to Keycloak
 * @param retryBackoff how long requests answer 503 after the enforcer failed to build, before one
 *     of them tries again
 */
@ConfigurationProperties("codehunters.security.authz")
public record KeycloakPolicyEnforcerProperties(
    boolean enabled,
    @DefaultValue("ENFORCING") EnforcementMode enforcementMode,
    String resource,
    String clientSecret,
    boolean lazyLoadPaths,
    boolean httpMethodAsScope,
    @DefaultValue PathCache pathCache,
    @DefaultValue Map<String, Map<String, Object>> claimInformationPoint,
    @DefaultValue List<PathMapping> paths,
    @DefaultValue("2s") Duration connectTimeout,
    @DefaultValue("3s") Duration readTimeout,
    @DefaultValue("50") int maxConnections,
    @DefaultValue("10s") Duration retryBackoff) {

  private static final String PREFIX = "codehunters.security.authz.";

  /** Requires the client credentials when enabled and positive limits. */
  public KeycloakPolicyEnforcerProperties {
    require(!enabled || hasText(resource), PREFIX + "resource is required when enabled");
    require(!enabled || hasText(clientSecret), PREFIX + "client-secret is required when enabled");
    claimInformationPoint = Map.copyOf(claimInformationPoint);
    paths = List.copyOf(paths);
    require(isPositive(connectTimeout), PREFIX + "connect-timeout must be positive");
    require(isPositive(readTimeout), PREFIX + "read-timeout must be positive");
    require(maxConnections > 0, PREFIX + "max-connections must be positive");
    require(isPositive(retryBackoff), PREFIX + "retry-backoff must be positive");
  }

  @Override
  public String toString() {
    return ("KeycloakPolicyEnforcerProperties[enabled=%s, enforcementMode=%s, resource=%s,"
            + " clientSecret=***, paths=%s]")
        .formatted(enabled, enforcementMode, resource, paths.size());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static boolean isPositive(Duration duration) {
    return duration != null && duration.isPositive();
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }

  /** Keycloak's enforcement modes. */
  public enum EnforcementMode {
    /** Requests are denied unless a mapped path's policies grant them. */
    ENFORCING,
    /** Requests to paths without a mapping are allowed; mapped paths follow their own mode. */
    PERMISSIVE,
    /** Policies are not evaluated. */
    DISABLED
  }

  /** How a method's scopes must be granted. */
  public enum ScopesEnforcementMode {
    /** Every scope must be granted. */
    ALL,
    /** One of the scopes is enough. */
    ANY
  }

  /**
   * Cache of the paths the enforcer has matched or resolved from Keycloak.
   *
   * @param maxEntries upper bound of cached paths
   * @param lifespan how long a cached path is reused
   */
  public record PathCache(
      @DefaultValue("1000") int maxEntries, @DefaultValue("30s") Duration lifespan) {

    /** Requires positive limits. */
    public PathCache {
      require(maxEntries > 0, PREFIX + "path-cache.max-entries must be positive");
      require(isPositive(lifespan), PREFIX + "path-cache.lifespan must be positive");
    }
  }

  /**
   * A protected path and the scopes each HTTP method requires.
   *
   * @param path Keycloak path pattern: {@code /*} matches any sub-path, {@code {id}} one segment
   * @param name Keycloak resource name
   * @param enforcementMode how this path is enforced
   * @param scopes scopes requested for methods {@code methods} does not list
   * @param methods scopes each HTTP method requires
   * @param claimInformationPoint claim information point applied to this path
   */
  public record PathMapping(
      String path,
      String name,
      @DefaultValue("ENFORCING") EnforcementMode enforcementMode,
      @DefaultValue List<String> scopes,
      @DefaultValue List<MethodMapping> methods,
      @DefaultValue Map<String, Map<String, Object>> claimInformationPoint) {

    /** Requires an absolute Keycloak pattern, the resource name and scopes for every method. */
    public PathMapping {
      require(hasText(path), PREFIX + "paths[].path is required");
      require(path.startsWith("/"), PREFIX + "paths[].path must start with '/': " + path);
      require(
          !path.contains("**"),
          PREFIX + "paths[].path " + path + " uses '**'; Keycloak patterns use '/*' for sub-paths");
      require(hasText(name), PREFIX + "paths[].name is required for " + path);
      scopes = List.copyOf(scopes);
      methods = List.copyOf(methods);
      claimInformationPoint = Map.copyOf(claimInformationPoint);
      methods.forEach(method -> requireScopes(path, method));
    }

    private static void requireScopes(String path, MethodMapping method) {
      require(
          !method.scopes().isEmpty(),
          "%spaths[].methods[].scopes must not be empty (path %s, method %s)"
              .formatted(PREFIX, path, method.method()));
    }
  }

  /**
   * Scopes required for one HTTP method.
   *
   * @param method HTTP method, case-insensitive
   * @param scopes Keycloak scopes the method requires; at least one
   * @param scopesEnforcementMode whether all scopes or any of them must be granted
   */
  public record MethodMapping(
      String method,
      @DefaultValue List<String> scopes,
      @DefaultValue("ALL") ScopesEnforcementMode scopesEnforcementMode) {

    /** Requires the method and upper-cases it, as Keycloak compares methods case-sensitively. */
    public MethodMapping {
      require(hasText(method), PREFIX + "paths[].methods[].method is required");
      method = method.toUpperCase(Locale.ROOT);
      scopes = List.copyOf(scopes);
    }
  }
}
