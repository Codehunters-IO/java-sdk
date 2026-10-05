package io.codehunters.sdk.security.authz;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the shared {@link KeycloakAuthzFilter} PEP, bound from the {@code
 * keycloak.policy-enforcer} prefix. Each consuming service supplies its own {@link #paths} catalog
 * mapping its protected endpoints to Keycloak Resources and Scopes.
 */
@ConfigurationProperties(prefix = "keycloak.policy-enforcer")
public class KeycloakPolicyEnforcerProperties {

  private boolean enabled = false;
  private String enforcementMode = "ENFORCING";
  private String resource;
  private String clientSecret;
  private boolean lazyLoadPaths = false;
  private List<PathMapping> paths = new ArrayList<>();

  /** Creates the properties with default values. */
  public KeycloakPolicyEnforcerProperties() {}

  /**
   * Whether the PEP filter is active for this service.
   *
   * @return {@code true} if the filter should be registered
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets whether the PEP filter is active.
   *
   * @param enabled {@code true} to register the filter
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Keycloak policy enforcer mode ({@code ENFORCING} or {@code PERMISSIVE}).
   *
   * @return the enforcement mode
   */
  public String getEnforcementMode() {
    return enforcementMode;
  }

  /**
   * Sets the enforcement mode.
   *
   * @param enforcementMode the enforcement mode
   */
  public void setEnforcementMode(String enforcementMode) {
    this.enforcementMode = enforcementMode;
  }

  /**
   * Keycloak client id used as the Authorization Services resource server for this service.
   *
   * @return the client id
   */
  public String getResource() {
    return resource;
  }

  /**
   * Sets the client id.
   *
   * @param resource the client id
   */
  public void setResource(String resource) {
    this.resource = resource;
  }

  /**
   * Client secret used to authenticate this service against Keycloak Authorization Services.
   *
   * @return the client secret
   */
  public String getClientSecret() {
    return clientSecret;
  }

  /**
   * Sets the client secret.
   *
   * @param clientSecret the client secret
   */
  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  /**
   * Whether to defer loading the resource/scope paths lazily from Keycloak.
   *
   * @return {@code true} to lazy-load paths
   */
  public boolean isLazyLoadPaths() {
    return lazyLoadPaths;
  }

  /**
   * Sets whether to lazy-load paths.
   *
   * @param lazyLoadPaths {@code true} to lazy-load paths
   */
  public void setLazyLoadPaths(boolean lazyLoadPaths) {
    this.lazyLoadPaths = lazyLoadPaths;
  }

  /**
   * This service's catalog of protected paths, mapped to Keycloak Resources and Scopes.
   *
   * @return the path mappings
   */
  public List<PathMapping> getPaths() {
    return paths;
  }

  /**
   * Sets the path mappings.
   *
   * @param paths the path mappings
   */
  public void setPaths(List<PathMapping> paths) {
    this.paths = paths;
  }

  /** One Ant-style path pattern mapped to a Keycloak Resource name and its method/scope rules. */
  public static class PathMapping {
    private String path;
    private String name;
    private List<MethodMapping> methods = new ArrayList<>();

    /** Creates the mapping with default values. */
    public PathMapping() {}

    /**
     * Ant-style path pattern (e.g. {@code /api/v1/admin/users/*}).
     *
     * @return the path pattern
     */
    public String getPath() {
      return path;
    }

    /**
     * Sets the path pattern.
     *
     * @param path the path pattern
     */
    public void setPath(String path) {
      this.path = path;
    }

    /**
     * Keycloak Resource name this path maps to.
     *
     * @return the resource name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the resource name.
     *
     * @param name the resource name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * HTTP method to required-scopes mappings for this path.
     *
     * @return the method mappings
     */
    public List<MethodMapping> getMethods() {
      return methods;
    }

    /**
     * Sets the method mappings.
     *
     * @param methods the method mappings
     */
    public void setMethods(List<MethodMapping> methods) {
      this.methods = methods;
    }
  }

  /** An HTTP method mapped to the Keycloak Scopes it requires on the enclosing Resource. */
  public static class MethodMapping {
    private String method;
    private List<String> scopes = new ArrayList<>();

    /** Creates the mapping with default values. */
    public MethodMapping() {}

    /**
     * HTTP method (e.g. {@code POST}).
     *
     * @return the HTTP method
     */
    public String getMethod() {
      return method;
    }

    /**
     * Sets the HTTP method.
     *
     * @param method the HTTP method
     */
    public void setMethod(String method) {
      this.method = method;
    }

    /**
     * Required Keycloak scopes for this method (e.g. {@code CREATE}, {@code READ}).
     *
     * @return the required scopes
     */
    public List<String> getScopes() {
      return scopes;
    }

    /**
     * Sets the required scopes.
     *
     * @param scopes the required scopes
     */
    public void setScopes(List<String> scopes) {
      this.scopes = scopes;
    }
  }
}
