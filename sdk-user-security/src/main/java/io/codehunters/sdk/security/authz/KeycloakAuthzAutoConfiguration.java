package io.codehunters.sdk.security.authz;

import java.util.Map;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Autoconfigures the shared {@link KeycloakAuthzFilter} PEP. Opt-in per service via {@code
 * keycloak.policy-enforcer.enabled=true}; each service supplies its own {@code
 * keycloak.policy-enforcer.paths} catalog.
 */
@AutoConfiguration
@ConditionalOnClass(AuthzClient.class)
@ConditionalOnProperty(prefix = "keycloak.policy-enforcer", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(KeycloakPolicyEnforcerProperties.class)
public class KeycloakAuthzAutoConfiguration {

  /** Creates the autoconfiguration. */
  public KeycloakAuthzAutoConfiguration() {}

  /**
   * Builds the Keycloak Authorization Services client for this service's resource server.
   *
   * @param authServerUrl Keycloak base URL
   * @param realm Keycloak realm
   * @param props the policy-enforcer properties (resource/client-secret)
   * @return the configured {@link AuthzClient}
   */
  @Bean
  @ConditionalOnMissingBean
  public AuthzClient keycloakAuthzClient(
      @Value("${keycloak.host}") String authServerUrl,
      @Value("${keycloak.realm}") String realm,
      KeycloakPolicyEnforcerProperties props) {
    var config =
        new Configuration(
            authServerUrl,
            realm,
            props.getResource(),
            Map.of("secret", props.getClientSecret()),
            null);
    return AuthzClient.create(config);
  }

  /**
   * Builds the shared PEP filter.
   *
   * @param authzClient client used to query Keycloak Authorization Services
   * @param props this service's path-to-resource/scope catalog
   * @return the filter instance
   */
  @Bean
  @ConditionalOnMissingBean
  public KeycloakAuthzFilter keycloakAuthzFilter(
      AuthzClient authzClient, KeycloakPolicyEnforcerProperties props) {
    return new KeycloakAuthzFilter(authzClient, props);
  }

  /**
   * Registers the PEP filter to run late in the chain, after identity has already been resolved
   * into the security context by {@code KeycloakSecurityFilter}.
   *
   * @param filter the PEP filter bean
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
}
