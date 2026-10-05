package io.codehunters.security.authz;

import io.codehunters.support.config.LegacyPropertyAliases;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Honours the 2.x {@code keycloak.policy-enforcer.*}, {@code keycloak.host} and {@code
 * keycloak.realm} keys under their 3.x names until 4.0.
 */
public class KeycloakAuthzLegacyProperties implements EnvironmentPostProcessor, Ordered {

  private static final LegacyPropertyAliases ALIASES =
      new LegacyPropertyAliases(
          "user-security-authz",
          Map.of(
              "keycloak.policy-enforcer", "codehunters.security.authz",
              "keycloak.host", "codehunters.security.url",
              "keycloak.realm", "codehunters.security.realm"),
          Map.of());

  private final Log log;

  /**
   * Creates the post-processor. Instantiated by Spring Boot.
   *
   * @param logFactory factory for logs written before logging is initialised
   */
  public KeycloakAuthzLegacyProperties(DeferredLogFactory logFactory) {
    this.log = logFactory.getLog(KeycloakAuthzLegacyProperties.class);
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    ALIASES.apply(environment, log);
  }

  @Override
  public int getOrder() {
    // After sdk-user-security's aliases, so keycloak.security.url wins over keycloak.host.
    return ConfigDataEnvironmentPostProcessor.ORDER + 2;
  }
}
