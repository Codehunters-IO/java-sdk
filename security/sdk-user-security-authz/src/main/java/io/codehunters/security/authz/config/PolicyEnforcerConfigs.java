package io.codehunters.security.authz.config;

import io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties.MethodMapping;
import io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties.PathMapping;
import io.codehunters.security.config.KeycloakSecurityProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig.EnforcementMode;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig.MethodConfig;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig.PathCacheConfig;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig.PathConfig;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig.ScopeEnforcementMode;

/** Maps {@code codehunters.security.*} properties onto Keycloak's {@link PolicyEnforcerConfig}. */
final class PolicyEnforcerConfigs {

  private PolicyEnforcerConfigs() {}

  static PolicyEnforcerConfig from(
      KeycloakSecurityProperties security, KeycloakPolicyEnforcerProperties properties) {
    var config = new PolicyEnforcerConfig();
    config.setAuthServerUrl(security.url());
    config.setRealm(security.realm());
    config.setResource(properties.resource());
    config.setCredentials(new HashMap<>(Map.of("secret", properties.clientSecret())));
    applyEnforcement(config, properties);
    return config;
  }

  private static void applyEnforcement(
      PolicyEnforcerConfig config, KeycloakPolicyEnforcerProperties properties) {
    config.setEnforcementMode(EnforcementMode.valueOf(properties.enforcementMode().name()));
    config.setLazyLoadPaths(properties.lazyLoadPaths());
    config.setHttpMethodAsScope(properties.httpMethodAsScope());
    config.setPathCacheConfig(pathCache(properties.pathCache()));
    config.setClaimInformationPointConfig(nullIfEmpty(properties.claimInformationPoint()));
    config.setPaths(
        new ArrayList<>(properties.paths().stream().map(PolicyEnforcerConfigs::path).toList()));
  }

  private static PathCacheConfig pathCache(KeycloakPolicyEnforcerProperties.PathCache cache) {
    var config = new PathCacheConfig();
    config.setMaxEntries(cache.maxEntries());
    config.setLifespan(cache.lifespan().toMillis());
    return config;
  }

  private static PathConfig path(PathMapping mapping) {
    var path = new PathConfig();
    path.setPath(mapping.path());
    path.setName(mapping.name());
    path.setEnforcementMode(EnforcementMode.valueOf(mapping.enforcementMode().name()));
    path.setScopes(new ArrayList<>(mapping.scopes()));
    path.setMethods(
        new ArrayList<>(mapping.methods().stream().map(PolicyEnforcerConfigs::method).toList()));
    path.setClaimInformationPointConfig(nullIfEmpty(mapping.claimInformationPoint()));
    return path;
  }

  private static MethodConfig method(MethodMapping mapping) {
    var method = new MethodConfig();
    method.setMethod(mapping.method());
    method.setScopes(new ArrayList<>(mapping.scopes()));
    method.setScopesEnforcementMode(
        ScopeEnforcementMode.valueOf(mapping.scopesEnforcementMode().name()));
    return method;
  }

  // Keycloak's enforcer tells "no claim information point" apart by null, not by an empty map.
  private static Map<String, Map<String, Object>> nullIfEmpty(
      Map<String, Map<String, Object>> claimInformationPoint) {
    return claimInformationPoint.isEmpty() ? null : new HashMap<>(claimInformationPoint);
  }
}
