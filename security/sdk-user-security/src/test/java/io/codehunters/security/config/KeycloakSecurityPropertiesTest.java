package io.codehunters.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class KeycloakSecurityPropertiesTest {

  private static final Map<String, String> REQUIRED =
      Map.of(
          "codehunters.security.url", "https://kc.example.com/",
          "codehunters.security.realm", "acme",
          "codehunters.security.audiences[0]", "svc");

  @Test
  void should_apply_secure_defaults_when_only_required_keys_are_set() {
    KeycloakSecurityProperties properties = bind(REQUIRED);

    assertThat(properties.enabled()).isTrue();
    assertThat(properties.allowAnyAudience()).isFalse();
    assertThat(properties.allowInsecureHttp()).isFalse();
    assertThat(properties.introspection().enabled()).isFalse();
    assertThat(properties.introspection().failOpen()).isFalse();
    assertThat(properties.introspection().cacheTtl()).isEqualTo(Duration.ofSeconds(30));
    assertThat(properties.introspection().cacheMaxEntries()).isEqualTo(10_000);
    assertThat(properties.introspection().connectTimeout()).isEqualTo(Duration.ofSeconds(2));
    assertThat(properties.introspection().readTimeout()).isEqualTo(Duration.ofSeconds(3));
    assertThat(properties.cors().allowedMethods())
        .containsExactly("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
    assertThat(properties.cors().maxAge()).isEqualTo(3600);
  }

  @Test
  void should_derive_realm_endpoints_when_url_has_trailing_slash() {
    KeycloakSecurityProperties properties = bind(REQUIRED);

    assertThat(properties.issuerUri()).isEqualTo("https://kc.example.com/realms/acme");
    assertThat(properties.jwkSetUri())
        .isEqualTo("https://kc.example.com/realms/acme/protocol/openid-connect/certs");
    assertThat(properties.introspectionUri())
        .isEqualTo("https://kc.example.com/realms/acme/protocol/openid-connect/token/introspect");
  }

  @Test
  void should_fail_when_url_is_missing() {
    assertThatThrownBy(() -> bind(without("codehunters.security.url")))
        .isInstanceOf(BindException.class)
        .rootCause()
        .hasMessageContaining("codehunters.security.url");
  }

  @Test
  void should_fail_when_url_is_not_http() {
    assertThatThrownBy(() -> bind(with("codehunters.security.url", "kc.example.com")))
        .rootCause()
        .hasMessageContaining("codehunters.security.url");
  }

  @Test
  void should_fail_when_url_is_plain_http_without_opt_in() {
    assertThatThrownBy(() -> bind(with("codehunters.security.url", "http://keycloak:8080")))
        .rootCause()
        .hasMessageContaining("must use https")
        .hasMessageContaining("codehunters.security.allow-insecure-http");
  }

  @Test
  void should_fail_when_jwk_set_uri_is_plain_http_without_opt_in() {
    Map<String, String> source =
        with(
            "codehunters.security.jwk-set-uri",
            "http://keycloak:8080/realms/acme/protocol/openid-connect/certs");

    assertThatThrownBy(() -> bind(source))
        .rootCause()
        .hasMessageContaining("codehunters.security.allow-insecure-http");
  }

  @Test
  void should_accept_https_url_regardless_of_scheme_case() {
    assertThat(bind(with("codehunters.security.url", "HTTPS://kc.example.com")).url())
        .isEqualTo("HTTPS://kc.example.com");
  }

  @Test
  void should_fail_when_realm_is_blank() {
    assertThatThrownBy(() -> bind(with("codehunters.security.realm", " ")))
        .rootCause()
        .hasMessageContaining("codehunters.security.realm");
  }

  @Test
  void should_fail_when_introspection_enabled_without_client_id() {
    assertThatThrownBy(() -> bind(with("codehunters.security.introspection.enabled", "true")))
        .rootCause()
        .hasMessageContaining("codehunters.security.introspection.client-id");
  }

  @Test
  void should_fail_when_introspection_enabled_without_client_secret() {
    Map<String, String> source = with("codehunters.security.introspection.enabled", "true");
    source.put("codehunters.security.introspection.client-id", "svc");

    assertThatThrownBy(() -> bind(source))
        .rootCause()
        .hasMessageContaining("codehunters.security.introspection.client-secret");
  }

  @Test
  void should_bind_client_credentials_when_introspection_enabled() {
    Map<String, String> source = with("codehunters.security.introspection.enabled", "true");
    source.put("codehunters.security.introspection.client-id", "svc");
    source.put("codehunters.security.introspection.client-secret", "secret");

    assertThat(bind(source).introspection().enabled()).isTrue();
  }

  @Test
  void should_fail_when_no_audience_is_configured() {
    assertThatThrownBy(() -> bind(without("codehunters.security.audiences[0]")))
        .rootCause()
        .hasMessageContaining("codehunters.security.audiences");
  }

  @Test
  void should_accept_any_audience_only_when_explicitly_allowed() {
    Map<String, String> source = without("codehunters.security.audiences[0]");
    source.put("codehunters.security.allow-any-audience", "true");

    assertThat(bind(source).audiences()).isEmpty();
  }

  @Test
  void should_use_explicit_issuer_and_jwk_set_uri_when_keycloak_is_reached_internally() {
    Map<String, String> source = with("codehunters.security.url", "http://keycloak:8080");
    source.put("codehunters.security.allow-insecure-http", "true");
    source.put("codehunters.security.issuer-uri", "https://login.example.com/realms/acme");
    source.put(
        "codehunters.security.jwk-set-uri",
        "http://keycloak:8080/realms/acme/protocol/openid-connect/certs");

    KeycloakSecurityProperties properties = bind(source);

    assertThat(properties.issuerUri()).isEqualTo("https://login.example.com/realms/acme");
    assertThat(properties.jwkSetUri())
        .isEqualTo("http://keycloak:8080/realms/acme/protocol/openid-connect/certs");
    assertThat(properties.introspectionUri())
        .isEqualTo("http://keycloak:8080/realms/acme/protocol/openid-connect/token/introspect");
  }

  @Test
  void should_fail_when_cache_max_entries_is_not_positive() {
    assertThatThrownBy(
            () -> bind(with("codehunters.security.introspection.cache-max-entries", "0")))
        .rootCause()
        .hasMessageContaining("cache-max-entries");
  }

  @Test
  void should_bind_lists_when_configured() {
    Map<String, String> source = new HashMap<>(REQUIRED);
    source.put("codehunters.security.audiences[1]", "web");
    source.put("codehunters.security.role-clients[0]", "svc");
    source.put("codehunters.security.public-paths[0]", "/public/**");
    source.put("codehunters.security.excluded-paths[0]", "/health");

    KeycloakSecurityProperties properties = bind(source);

    assertThat(properties.audiences()).isEqualTo(List.of("svc", "web"));
    assertThat(properties.roleClients()).isEqualTo(List.of("svc"));
    assertThat(properties.publicPaths()).isEqualTo(List.of("/public/**"));
    assertThat(properties.excludedPaths()).isEqualTo(List.of("/health"));
  }

  private static KeycloakSecurityProperties bind(Map<String, String> source) {
    return new Binder(new MapConfigurationPropertySource(source))
        .bindOrCreate("codehunters.security", KeycloakSecurityProperties.class);
  }

  private static Map<String, String> with(String key, String value) {
    Map<String, String> source = new HashMap<>(REQUIRED);
    source.put(key, value);
    return source;
  }

  private static Map<String, String> without(String key) {
    Map<String, String> source = new HashMap<>(REQUIRED);
    source.remove(key);
    return source;
  }
}
