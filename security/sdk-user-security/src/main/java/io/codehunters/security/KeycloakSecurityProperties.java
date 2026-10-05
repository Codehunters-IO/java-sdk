package io.codehunters.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the user-security SDK, bound from the {@code keycloak.security} prefix. Covers
 * both the gateway-trust filter (public/excluded paths, CORS) and per-request Keycloak session
 * validation via RFC 7662 token introspection.
 *
 * <p>Session validation rejects requests whose access token belongs to a revoked Keycloak session
 * even though the JWT itself has not yet expired — closing the single-active-session gap left by
 * stateless JWT validation at the edge. Validation is mandatory by policy.
 */
@ConfigurationProperties(prefix = "keycloak.security")
public class KeycloakSecurityProperties {

  private List<String> publicPaths = new ArrayList<>();
  private List<String> excludedPaths = new ArrayList<>();
  private Cors cors = new Cors();

  private String url;
  private String realm;
  private String clientId;
  private String clientSecret;
  private long cacheTtlSeconds = 30;
  private boolean failOpen = true;

  /** Creates the properties with default values. */
  public KeycloakSecurityProperties() {}

  /**
   * Paths exposed to anonymous callers.
   *
   * @return the public path patterns
   */
  public List<String> getPublicPaths() {
    return publicPaths;
  }

  /**
   * Sets the public path patterns.
   *
   * @param publicPaths the patterns
   */
  public void setPublicPaths(List<String> publicPaths) {
    this.publicPaths = publicPaths;
  }

  /**
   * Paths for which the security filter is skipped entirely.
   *
   * @return the excluded path patterns
   */
  public List<String> getExcludedPaths() {
    return excludedPaths;
  }

  /**
   * Sets the excluded path patterns.
   *
   * @param excludedPaths the patterns
   */
  public void setExcludedPaths(List<String> excludedPaths) {
    this.excludedPaths = excludedPaths;
  }

  /**
   * CORS configuration.
   *
   * @return the CORS configuration
   */
  public Cors getCors() {
    return cors;
  }

  /**
   * Sets the CORS configuration.
   *
   * @param cors the CORS configuration
   */
  public void setCors(Cors cors) {
    this.cors = cors;
  }

  /**
   * Keycloak base URL, e.g. {@code https://kc} (env {@code KEYCLOAK_URL}).
   *
   * @return the Keycloak base URL
   */
  public String getUrl() {
    return url;
  }

  /**
   * Sets the Keycloak base URL.
   *
   * @param url the base URL
   */
  public void setUrl(String url) {
    this.url = url;
  }

  /**
   * Keycloak realm that issued the tokens (env {@code KEYCLOAK_REALM}).
   *
   * @return the realm
   */
  public String getRealm() {
    return realm;
  }

  /**
   * Sets the realm.
   *
   * @param realm the realm
   */
  public void setRealm(String realm) {
    this.realm = realm;
  }

  /**
   * Confidential client id (realm client) used to authenticate the introspection call (env {@code
   * KEYCLOAK_CLIENT_ID}).
   *
   * @return the client id
   */
  public String getClientId() {
    return clientId;
  }

  /**
   * Sets the introspection client id.
   *
   * @param clientId the client id
   */
  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  /**
   * Confidential client secret used to authenticate the introspection call (env {@code
   * KEYCLOAK_CLIENT_SECRET}).
   *
   * @return the client secret
   */
  public String getClientSecret() {
    return clientSecret;
  }

  /**
   * Sets the introspection client secret.
   *
   * @param clientSecret the client secret
   */
  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  /**
   * How long an introspection result is cached, in seconds. Bounds Keycloak load and caps
   * invalidation latency to this value.
   *
   * @return the cache TTL in seconds
   */
  public long getCacheTtlSeconds() {
    return cacheTtlSeconds;
  }

  /**
   * Sets the cache TTL in seconds.
   *
   * @param cacheTtlSeconds the TTL
   */
  public void setCacheTtlSeconds(long cacheTtlSeconds) {
    this.cacheTtlSeconds = cacheTtlSeconds;
  }

  /**
   * When Keycloak is unreachable: {@code true} allows the request (availability first), {@code
   * false} rejects it (security first).
   *
   * @return {@code true} to fail open
   */
  public boolean isFailOpen() {
    return failOpen;
  }

  /**
   * Sets the fail-open behavior.
   *
   * @param failOpen {@code true} to fail open
   */
  public void setFailOpen(boolean failOpen) {
    this.failOpen = failOpen;
  }

  /**
   * Token introspection endpoint derived from {@link #getUrl()} and {@link #getRealm()}.
   *
   * @return the RFC 7662 introspection URI
   */
  public String getIntrospectionUri() {
    String base = url == null ? "" : url.replaceAll("/+$", "");
    return base + "/realms/" + realm + "/protocol/openid-connect/token/introspect";
  }

  /** CORS-specific configuration. */
  public static class Cors {

    private List<String> allowedOrigins = new ArrayList<>();

    private List<String> allowedMethods =
        List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");

    private List<String> allowedHeaders = List.of("*");

    private boolean allowCredentials = false;

    private long maxAge = 3600;

    /** Creates the CORS configuration with default values. */
    public Cors() {}

    /**
     * Origins permitted to make cross-origin requests.
     *
     * @return the allowed origins
     */
    public List<String> getAllowedOrigins() {
      return allowedOrigins;
    }

    /**
     * Sets the allowed origins.
     *
     * @param allowedOrigins the allowed origins
     */
    public void setAllowedOrigins(List<String> allowedOrigins) {
      this.allowedOrigins = allowedOrigins;
    }

    /**
     * HTTP methods permitted on cross-origin requests.
     *
     * @return the allowed methods
     */
    public List<String> getAllowedMethods() {
      return allowedMethods;
    }

    /**
     * Sets the allowed HTTP methods.
     *
     * @param allowedMethods the allowed methods
     */
    public void setAllowedMethods(List<String> allowedMethods) {
      this.allowedMethods = allowedMethods;
    }

    /**
     * Request headers permitted on cross-origin requests.
     *
     * @return the allowed headers
     */
    public List<String> getAllowedHeaders() {
      return allowedHeaders;
    }

    /**
     * Sets the allowed request headers.
     *
     * @param allowedHeaders the allowed headers
     */
    public void setAllowedHeaders(List<String> allowedHeaders) {
      this.allowedHeaders = allowedHeaders;
    }

    /**
     * Whether credentials are allowed on cross-origin requests.
     *
     * @return {@code true} if credentials are allowed
     */
    public boolean isAllowCredentials() {
      return allowCredentials;
    }

    /**
     * Sets whether credentials are allowed.
     *
     * @param allowCredentials {@code true} to allow credentials
     */
    public void setAllowCredentials(boolean allowCredentials) {
      this.allowCredentials = allowCredentials;
    }

    /**
     * Cache age for the CORS preflight response, in seconds.
     *
     * @return the preflight cache max-age
     */
    public long getMaxAge() {
      return maxAge;
    }

    /**
     * Sets the preflight cache max-age in seconds.
     *
     * @param maxAge the max-age
     */
    public void setMaxAge(long maxAge) {
      this.maxAge = maxAge;
    }
  }
}
