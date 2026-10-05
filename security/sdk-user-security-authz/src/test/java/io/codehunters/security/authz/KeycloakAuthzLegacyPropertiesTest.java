package io.codehunters.security.authz;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.security.authz.KeycloakPolicyEnforcerProperties.EnforcementMode;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class KeycloakAuthzLegacyPropertiesTest {

  private final StandardEnvironment environment = new StandardEnvironment();

  @Test
  void should_bind_2x_policy_enforcer_keys_to_the_3x_properties() {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "application",
                Map.of(
                    "keycloak.host", "https://kc.example.com",
                    "keycloak.realm", "acme",
                    "keycloak.policy-enforcer.enabled", "true",
                    "keycloak.policy-enforcer.enforcement-mode", "PERMISSIVE",
                    "keycloak.policy-enforcer.resource", "svc",
                    "keycloak.policy-enforcer.client-secret", "secret",
                    "keycloak.policy-enforcer.paths[0].path", "/api/**",
                    "keycloak.policy-enforcer.paths[0].name", "api",
                    "keycloak.policy-enforcer.paths[0].methods[0].method", "GET",
                    "keycloak.policy-enforcer.paths[0].methods[0].scopes[0]", "READ")));

    new KeycloakAuthzLegacyProperties(Supplier::get)
        .postProcessEnvironment(environment, new SpringApplication());

    Binder binder = Binder.get(environment);
    KeycloakPolicyEnforcerProperties properties =
        binder.bindOrCreate("codehunters.security.authz", KeycloakPolicyEnforcerProperties.class);
    assertThat(properties.enabled()).isTrue();
    assertThat(properties.enforcementMode()).isEqualTo(EnforcementMode.PERMISSIVE);
    assertThat(properties.resource()).isEqualTo("svc");
    assertThat(properties.paths()).singleElement().satisfies(p -> assertThat(p.name()).isEqualTo("api"));
    assertThat(environment.getProperty("codehunters.security.url")).isEqualTo("https://kc.example.com");
    assertThat(environment.getProperty("codehunters.security.realm")).isEqualTo("acme");
  }
}
