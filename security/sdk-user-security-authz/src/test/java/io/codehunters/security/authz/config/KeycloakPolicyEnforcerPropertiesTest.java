package io.codehunters.security.authz.config;

import static io.codehunters.security.authz.config.Bindings.authz;
import static io.codehunters.security.authz.config.Bindings.authzWithCredentials;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties.EnforcementMode;
import io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties.ScopesEnforcementMode;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeycloakPolicyEnforcerPropertiesTest {

  private static final String PREFIX = "codehunters.security.authz.";

  @Test
  void should_apply_defaults_when_only_credentials_are_set() {
    var properties = authzWithCredentials(Map.of());

    assertThat(properties.enforcementMode()).isEqualTo(EnforcementMode.ENFORCING);
    assertThat(properties.lazyLoadPaths()).isFalse();
    assertThat(properties.httpMethodAsScope()).isFalse();
    assertThat(properties.pathCache().maxEntries()).isEqualTo(1000);
    assertThat(properties.pathCache().lifespan()).isEqualTo(Duration.ofSeconds(30));
    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(3));
    assertThat(properties.maxConnections()).isEqualTo(50);
    assertThat(properties.retryBackoff()).isEqualTo(Duration.ofSeconds(10));
    assertThat(properties.paths()).isEmpty();
    assertThat(properties.claimInformationPoint()).isEmpty();
    assertThat(properties.skipPaths()).isEmpty();
  }

  @Test
  void should_bind_skip_paths() {
    var properties = authzWithCredentials(Map.of(PREFIX + "skip-paths[0]", "/actuator/**"));

    assertThat(properties.skipPaths()).containsExactly("/actuator/**");
  }

  @Test
  void should_default_path_and_method_modes_when_unset() {
    var properties =
        authzWithCredentials(
            Map.of(
                PREFIX + "paths[0].path", "/api/users/*",
                PREFIX + "paths[0].name", "users",
                PREFIX + "paths[0].methods[0].method", "get",
                PREFIX + "paths[0].methods[0].scopes[0]", "READ"));

    var path = properties.paths().getFirst();
    assertThat(path.enforcementMode()).isEqualTo(EnforcementMode.ENFORCING);
    assertThat(path.methods().getFirst().method()).isEqualTo("GET");
    assertThat(path.methods().getFirst().scopesEnforcementMode())
        .isEqualTo(ScopesEnforcementMode.ALL);
  }

  @Test
  void should_fail_when_enabled_without_resource() {
    assertThatThrownBy(
            () -> authz(Map.of(PREFIX + "enabled", "true", PREFIX + "client-secret", "s3cret")))
        .rootCause()
        .hasMessageContaining(PREFIX + "resource");
  }

  @Test
  void should_fail_when_enabled_without_client_secret() {
    assertThatThrownBy(() -> authz(Map.of(PREFIX + "enabled", "true", PREFIX + "resource", "svc")))
        .rootCause()
        .hasMessageContaining(PREFIX + "client-secret");
  }

  @Test
  void should_fail_when_method_mapping_has_no_scopes() {
    assertThatThrownBy(
            () ->
                authzWithCredentials(
                    Map.of(
                        PREFIX + "paths[0].path", "/api/users/*",
                        PREFIX + "paths[0].name", "users",
                        PREFIX + "paths[0].methods[0].method", "DELETE")))
        .rootCause()
        .hasMessageContaining("scopes must not be empty")
        .hasMessageContaining("/api/users/*")
        .hasMessageContaining("DELETE");
  }

  @Test
  void should_fail_when_path_uses_ant_double_wildcard() {
    assertThatThrownBy(
            () ->
                authzWithCredentials(
                    Map.of(PREFIX + "paths[0].path", "/api/**", PREFIX + "paths[0].name", "users")))
        .rootCause()
        .hasMessageContaining("'**'");
  }

  @Test
  void should_fail_when_path_is_not_absolute() {
    assertThatThrownBy(
            () ->
                authzWithCredentials(
                    Map.of(PREFIX + "paths[0].path", "api/*", PREFIX + "paths[0].name", "users")))
        .rootCause()
        .hasMessageContaining("must start with '/'");
  }

  @Test
  void should_fail_when_path_has_no_resource_name() {
    assertThatThrownBy(() -> authzWithCredentials(Map.of(PREFIX + "paths[0].path", "/api/*")))
        .rootCause()
        .hasMessageContaining(PREFIX + "paths[].name");
  }

  @Test
  void should_fail_when_enforcement_mode_is_unknown() {
    assertThatThrownBy(() -> authzWithCredentials(Map.of(PREFIX + "enforcement-mode", "LENIENT")))
        .hasMessageContaining(PREFIX + "enforcement-mode");
  }

  @Test
  void should_fail_when_scopes_enforcement_mode_is_disabled() {
    assertThatThrownBy(
            () ->
                authzWithCredentials(
                    Map.of(
                        PREFIX + "paths[0].path", "/api/users/*",
                        PREFIX + "paths[0].name", "users",
                        PREFIX + "paths[0].methods[0].method", "GET",
                        PREFIX + "paths[0].methods[0].scopes[0]", "READ",
                        PREFIX + "paths[0].methods[0].scopes-enforcement-mode", "DISABLED")))
        .hasMessageContaining("scopes-enforcement-mode");
  }

  @Test
  void should_fail_when_path_cache_max_entries_is_not_positive() {
    assertThatThrownBy(() -> authzWithCredentials(Map.of(PREFIX + "path-cache.max-entries", "0")))
        .rootCause()
        .hasMessageContaining(PREFIX + "path-cache.max-entries");
  }

  @Test
  void should_fail_when_read_timeout_is_not_positive() {
    assertThatThrownBy(() -> authzWithCredentials(Map.of(PREFIX + "read-timeout", "0s")))
        .rootCause()
        .hasMessageContaining(PREFIX + "read-timeout");
  }

  @Test
  void should_fail_when_max_connections_is_not_positive() {
    assertThatThrownBy(() -> authzWithCredentials(Map.of(PREFIX + "max-connections", "0")))
        .rootCause()
        .hasMessageContaining(PREFIX + "max-connections");
  }

  @Test
  void should_fail_when_retry_backoff_is_not_positive() {
    assertThatThrownBy(() -> authzWithCredentials(Map.of(PREFIX + "retry-backoff", "0s")))
        .rootCause()
        .hasMessageContaining(PREFIX + "retry-backoff");
  }

  @Test
  void should_mask_client_secret_when_printed() {
    assertThat(authzWithCredentials(Map.of()).toString())
        .doesNotContain("s3cret")
        .contains("clientSecret=***");
  }
}
