package io.codehunters.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the user-security SDK, bound from {@code codehunters.security}. Access tokens
 * are verified locally against the realm's published keys; token introspection additionally rejects
 * tokens whose Keycloak session was revoked.
 *
 * @param enabled whether the SDK configures request security at all
 * @param url Keycloak base URL, e.g. {@code https://kc.example.com}
 * @param realm realm that issues the access tokens
 * @param audiences accepted {@code aud} values; empty accepts any audience
 * @param roleClients clients whose {@code resource_access} roles become authorities, in addition to
 *     the realm roles
 * @param publicPaths path patterns open to anonymous callers; a bearer token, when sent, must still
 *     be valid
 * @param excludedPaths path patterns that bypass request security entirely
 * @param cors CORS configuration
 * @param introspection per-request session validation
 */
@ConfigurationProperties("codehunters.security")
public record KeycloakSecurityProperties(
    @DefaultValue("true") boolean enabled,
    String url,
    String realm,
    @DefaultValue List<String> audiences,
    @DefaultValue List<String> roleClients,
    @DefaultValue List<String> publicPaths,
    @DefaultValue List<String> excludedPaths,
    @DefaultValue Cors cors,
    @DefaultValue Introspection introspection) {

  private static final String PREFIX = "codehunters.security.";

  /** Validates the realm coordinates and freezes the collections. */
  public KeycloakSecurityProperties {
    require(
        url != null && url.matches("(?i)https?://\\S+"),
        PREFIX + "url must be an absolute http(s) URL");
    require(realm != null && !realm.isBlank(), PREFIX + "realm is required");
    audiences = List.copyOf(audiences);
    roleClients = List.copyOf(roleClients);
    publicPaths = List.copyOf(publicPaths);
    excludedPaths = List.copyOf(excludedPaths);
  }

  /**
   * Expected {@code iss} claim of the realm's tokens.
   *
   * @return the issuer URI
   */
  public String issuer() {
    return url.replaceAll("/+$", "") + "/realms/" + realm;
  }

  /**
   * Endpoint publishing the realm's signing keys.
   *
   * @return the JWK set URI
   */
  public String jwkSetUri() {
    return issuer() + "/protocol/openid-connect/certs";
  }

  /**
   * RFC 7662 token introspection endpoint of the realm.
   *
   * @return the introspection URI
   */
  public String introspectionUri() {
    return issuer() + "/protocol/openid-connect/token/introspect";
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }

  /**
   * CORS configuration applied to every path.
   *
   * @param allowedOrigins origins allowed to call the service
   * @param allowedMethods HTTP methods allowed on cross-origin requests
   * @param allowedHeaders request headers allowed on cross-origin requests
   * @param allowCredentials whether credentials are allowed on cross-origin requests
   * @param maxAge preflight cache age, in seconds
   */
  public record Cors(
      @DefaultValue List<String> allowedOrigins,
      @DefaultValue({"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"})
          List<String> allowedMethods,
      @DefaultValue("*") List<String> allowedHeaders,
      boolean allowCredentials,
      @DefaultValue("3600") long maxAge) {}

  /**
   * Per-request session validation through Keycloak token introspection.
   *
   * @param enabled whether tokens are checked against their Keycloak session
   * @param clientId confidential client that authenticates the introspection call
   * @param clientSecret secret of that client
   * @param cacheTtl how long a result is reused; also the worst-case revocation latency
   * @param cacheMaxEntries upper bound of cached results
   * @param failOpen whether to accept a verified token when Keycloak cannot be reached
   * @param connectTimeout connect timeout of the introspection call
   * @param readTimeout read timeout of the introspection call
   */
  public record Introspection(
      @DefaultValue("true") boolean enabled,
      String clientId,
      String clientSecret,
      @DefaultValue("30s") Duration cacheTtl,
      @DefaultValue("10000") int cacheMaxEntries,
      boolean failOpen,
      @DefaultValue("2s") Duration connectTimeout,
      @DefaultValue("3s") Duration readTimeout) {

    private static final String PREFIX = "codehunters.security.introspection.";

    /** Requires client credentials when enabled and positive limits. */
    public Introspection {
      require(!enabled || hasText(clientId), PREFIX + "client-id is required when enabled");
      require(!enabled || hasText(clientSecret), PREFIX + "client-secret is required when enabled");
      require(cacheMaxEntries > 0, PREFIX + "cache-max-entries must be positive");
      require(isPositive(cacheTtl), PREFIX + "cache-ttl must be positive");
      require(isPositive(connectTimeout), PREFIX + "connect-timeout must be positive");
      require(isPositive(readTimeout), PREFIX + "read-timeout must be positive");
    }

    @Override
    public String toString() {
      return "Introspection[enabled=%s, clientId=%s, clientSecret=***, cacheTtl=%s, failOpen=%s]"
          .formatted(enabled, clientId, cacheTtl, failOpen);
    }

    private static boolean hasText(String value) {
      return value != null && !value.isBlank();
    }

    private static boolean isPositive(Duration duration) {
      return duration != null && duration.isPositive();
    }
  }
}
