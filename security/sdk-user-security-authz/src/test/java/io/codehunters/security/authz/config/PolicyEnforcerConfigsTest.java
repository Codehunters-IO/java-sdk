package io.codehunters.security.authz.config;

import static io.codehunters.security.authz.config.Bindings.authzWithCredentials;
import static io.codehunters.security.authz.config.Bindings.security;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig.EnforcementMode;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig.ScopeEnforcementMode;

class PolicyEnforcerConfigsTest {

  private static final String PREFIX = "codehunters.security.authz.";
  private static final String KEYCLOAK_URL = "https://kc.example.com";

  private static final Map<String, String> CATALOG =
      Map.ofEntries(
          Map.entry(PREFIX + "enforcement-mode", "PERMISSIVE"),
          Map.entry(PREFIX + "lazy-load-paths", "true"),
          Map.entry(PREFIX + "http-method-as-scope", "true"),
          Map.entry(PREFIX + "path-cache.max-entries", "500"),
          Map.entry(PREFIX + "path-cache.lifespan", "1m"),
          Map.entry(
              PREFIX + "claim-information-point.claims.tenant", "{request.header['X-Tenant']}"),
          Map.entry(PREFIX + "paths[0].path", "/api/users/{id}"),
          Map.entry(PREFIX + "paths[0].name", "users"),
          Map.entry(PREFIX + "paths[0].enforcement-mode", "DISABLED"),
          Map.entry(PREFIX + "paths[0].scopes[0]", "VIEW"),
          Map.entry(PREFIX + "paths[0].claim-information-point.claims.region", "eu"),
          Map.entry(PREFIX + "paths[0].methods[0].method", "post"),
          Map.entry(PREFIX + "paths[0].methods[0].scopes[0]", "CREATE"),
          Map.entry(PREFIX + "paths[0].methods[0].scopes[1]", "WRITE"),
          Map.entry(PREFIX + "paths[0].methods[0].scopes-enforcement-mode", "ANY"));

  private final PolicyEnforcerConfig defaults =
      PolicyEnforcerConfigs.from(security(KEYCLOAK_URL), authzWithCredentials(Map.of()));

  private final PolicyEnforcerConfig catalog =
      PolicyEnforcerConfigs.from(security(KEYCLOAK_URL), authzWithCredentials(CATALOG));

  @Test
  void should_take_server_and_realm_from_user_security_properties() {
    assertThat(defaults.getAuthServerUrl()).isEqualTo(KEYCLOAK_URL);
    assertThat(defaults.getRealm()).isEqualTo("test");
  }

  @Test
  void should_map_client_id_and_secret() {
    assertThat(defaults.getResource()).isEqualTo("svc");
    assertThat(defaults.getCredentials()).containsEntry("secret", "s3cret");
  }

  @Test
  void should_enable_path_cache_with_defaults() {
    assertThat(defaults.getPathCacheConfig().getMaxEntries()).isEqualTo(1000);
    assertThat(defaults.getPathCacheConfig().getLifespan()).isEqualTo(30_000L);
  }

  @Test
  void should_leave_claim_information_point_unset_when_none_is_configured() {
    assertThat(defaults.getClaimInformationPointConfig()).isNull();
  }

  @Test
  void should_map_enforcer_settings() {
    assertThat(catalog.getEnforcementMode()).isEqualTo(EnforcementMode.PERMISSIVE);
    assertThat(catalog.getLazyLoadPaths()).isTrue();
    assertThat(catalog.getHttpMethodAsScope()).isTrue();
    assertThat(catalog.getPathCacheConfig().getMaxEntries()).isEqualTo(500);
    assertThat(catalog.getPathCacheConfig().getLifespan()).isEqualTo(60_000L);
    assertThat(catalog.getClaimInformationPointConfig())
        .containsEntry("claims", Map.of("tenant", "{request.header['X-Tenant']}"));
  }

  @Test
  void should_map_path_settings() {
    var path = catalog.getPaths().getFirst();

    assertThat(path.getPath()).isEqualTo("/api/users/{id}");
    assertThat(path.getName()).isEqualTo("users");
    assertThat(path.getEnforcementMode()).isEqualTo(EnforcementMode.DISABLED);
    assertThat(path.getScopes()).containsExactly("VIEW");
    assertThat(path.getClaimInformationPointConfig())
        .containsEntry("claims", Map.of("region", "eu"));
  }

  @Test
  void should_map_method_scopes_in_upper_case() {
    var method = catalog.getPaths().getFirst().getMethods().getFirst();

    assertThat(method.getMethod()).isEqualTo("POST");
    assertThat(method.getScopes()).containsExactly("CREATE", "WRITE");
    assertThat(method.getScopesEnforcementMode()).isEqualTo(ScopeEnforcementMode.ANY);
  }
}
