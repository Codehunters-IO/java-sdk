package io.codehunters.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;

class KeycloakSecurityLegacyPropertiesTest {

  private final StandardEnvironment environment = new StandardEnvironment();

  @Test
  void should_bind_2x_keys_to_the_3x_properties() {
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "application",
                Map.of(
                    "keycloak.security.url", "https://kc.example.com",
                    "keycloak.security.realm", "acme",
                    "keycloak.security.client-id", "svc",
                    "keycloak.security.client-secret", "secret",
                    "keycloak.security.cache-ttl-seconds", "45",
                    "keycloak.security.fail-open", "true",
                    "keycloak.security.public-paths[0]", "/public/**",
                    "keycloak.security.cors.allowed-origins[0]", "https://app.example.com")));

    new KeycloakSecurityLegacyProperties(Supplier::get)
        .postProcessEnvironment(environment, new SpringApplication());

    KeycloakSecurityProperties properties =
        Binder.get(environment)
            .bindOrCreate("codehunters.security", KeycloakSecurityProperties.class);
    assertThat(properties.issuer()).isEqualTo("https://kc.example.com/realms/acme");
    assertThat(properties.introspection().clientId()).isEqualTo("svc");
    assertThat(properties.introspection().clientSecret()).isEqualTo("secret");
    assertThat(properties.introspection().cacheTtl()).isEqualTo(Duration.ofSeconds(45));
    assertThat(properties.introspection().failOpen()).isTrue();
    assertThat(properties.publicPaths()).isEqualTo(List.of("/public/**"));
    assertThat(properties.cors().allowedOrigins()).isEqualTo(List.of("https://app.example.com"));
  }

  @Test
  void should_be_discovered_by_spring_boot() {
    DeferredLogFactory logFactory = Supplier::get;

    var postProcessors =
        SpringFactoriesLoader.forDefaultResourceLocation()
            .load(
                EnvironmentPostProcessor.class,
                ArgumentResolver.of(DeferredLogFactory.class, logFactory));

    assertThat(postProcessors).hasAtLeastOneElementOfType(KeycloakSecurityLegacyProperties.class);
  }
}
