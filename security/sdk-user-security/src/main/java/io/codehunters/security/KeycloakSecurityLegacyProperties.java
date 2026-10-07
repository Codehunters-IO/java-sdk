package io.codehunters.security;

import io.codehunters.support.config.LegacyPropertyAliases;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Honours the 2.x {@code keycloak.security.*} keys under their 3.x names until 4.0. Runs right
 * after the application's configuration files are loaded.
 */
public class KeycloakSecurityLegacyProperties implements EnvironmentPostProcessor, Ordered {

  private static final LegacyPropertyAliases ALIASES =
      new LegacyPropertyAliases(
          "user-security",
          Map.of(
              "keycloak.security", "codehunters.security",
              "keycloak.security.client-id", "codehunters.security.introspection.client-id",
              "keycloak.security.client-secret", "codehunters.security.introspection.client-secret",
              "keycloak.security.cache-ttl-seconds", "codehunters.security.introspection.cache-ttl",
              "keycloak.security.fail-open", "codehunters.security.introspection.fail-open"),
          Map.of("keycloak.security.cache-ttl-seconds", seconds -> seconds.trim() + "s"),
          List.of(
              "keycloak.security.url",
              "keycloak.security.realm",
              "keycloak.security.client-id",
              "keycloak.security.client-secret",
              "keycloak.security.cache-ttl-seconds",
              "keycloak.security.fail-open",
              "keycloak.security.public-paths",
              "keycloak.security.excluded-paths",
              "keycloak.security.cors.allowed-origins",
              "keycloak.security.cors.allowed-methods",
              "keycloak.security.cors.allowed-headers",
              "keycloak.security.cors.allow-credentials",
              "keycloak.security.cors.max-age"));

  private final Log log;

  /**
   * Creates the post-processor. Instantiated by Spring Boot.
   *
   * @param logFactory factory for logs written before logging is initialised
   */
  public KeycloakSecurityLegacyProperties(DeferredLogFactory logFactory) {
    this.log = logFactory.getLog(KeycloakSecurityLegacyProperties.class);
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    ALIASES.apply(environment, log);
  }

  @Override
  public int getOrder() {
    return ConfigDataEnvironmentPostProcessor.ORDER + 1;
  }
}
