package io.codehunters.security.authz;

import io.codehunters.security.config.KeycloakSecurityAutoConfiguration;
import io.codehunters.security.config.KeycloakSecurityProperties;
import java.time.Clock;
import java.util.function.Supplier;
import org.keycloak.authorization.client.AuthzClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Registers the Keycloak policy enforcement point. Opt-in per service with {@code
 * codehunters.security.authz.enabled=true}; each service supplies its own path catalog.
 *
 * <p>The Keycloak client is created on the first enforced request rather than at startup, so a
 * service starts while Keycloak is unreachable. Its calls have connect and read timeouts, and a
 * failed creation is retried only after {@code retry-backoff}.
 */
@AutoConfiguration(after = KeycloakSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(AuthzClient.class)
@ConditionalOnBooleanProperty("codehunters.security.authz.enabled")
@ConditionalOnBean(KeycloakSecurityProperties.class)
@EnableConfigurationProperties(KeycloakPolicyEnforcerProperties.class)
public class KeycloakAuthzAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public KeycloakAuthzAutoConfiguration() {}

  /**
   * Builds the PEP filter.
   *
   * @param security Keycloak URL and realm
   * @param properties the catalog and enforcement mode
   * @param authzClient an application-provided client, when present
   * @return the filter
   */
  @Bean
  @ConditionalOnMissingBean
  public KeycloakAuthzFilter keycloakAuthzFilter(
      KeycloakSecurityProperties security,
      KeycloakPolicyEnforcerProperties properties,
      ObjectProvider<AuthzClient> authzClient) {
    Supplier<AuthzClient> client =
        new BackoffSupplier<>(
            () -> authzClient.getIfAvailable(() -> create(security, properties)),
            properties.retryBackoff(),
            Clock.systemUTC());
    return new KeycloakAuthzFilter(client, properties);
  }

  /**
   * Registers the PEP late in the servlet chain, after Spring Security has authenticated the
   * request.
   *
   * @param filter the PEP filter
   * @return the registration
   */
  @Bean
  public FilterRegistrationBean<KeycloakAuthzFilter> keycloakAuthzFilterRegistration(
      KeycloakAuthzFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
    registration.addUrlPatterns("/*");
    return registration;
  }

  private static AuthzClient create(
      KeycloakSecurityProperties security, KeycloakPolicyEnforcerProperties properties) {
    return KeycloakAuthzClients.create(
        security.url(),
        security.realm(),
        properties.resource(),
        properties.clientSecret(),
        properties.connectTimeout(),
        properties.readTimeout());
  }
}
