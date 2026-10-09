package io.codehunters.security.authz.config;

import io.codehunters.security.authz.servlet.KeycloakPolicyEnforcerFilter;
import io.codehunters.security.config.KeycloakSecurityAutoConfiguration;
import io.codehunters.security.config.KeycloakSecurityProperties;
import java.time.Duration;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.keycloak.adapters.authorization.PolicyEnforcer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Adds Keycloak's official policy enforcer to the Spring Security filter chain. Opt-in per service
 * with {@code codehunters.security.authz.enabled=true}; each service supplies its own path catalog.
 *
 * <p>The enforcer is placed right after Spring Security's {@link AuthorizationFilter}, through a
 * {@code Customizer<HttpSecurity>} bean that Spring Security applies to every {@link HttpSecurity}
 * it builds: the sdk-user-security chains and any chain the application defines. Requests are
 * therefore authenticated (or rejected with 401) before Keycloak is asked; requests without a
 * bearer token, authenticated by another chain, pass through untouched.
 *
 * <p>The enforcer is built on the first enforced request, so a service starts while Keycloak is
 * unreachable. Calls to Keycloak have connect and read timeouts and a bounded connection pool.
 */
@AutoConfiguration(after = KeycloakSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({PolicyEnforcer.class, SecurityFilterChain.class})
@ConditionalOnBooleanProperty("codehunters.security.authz.enabled")
@ConditionalOnBean(KeycloakSecurityProperties.class)
@EnableConfigurationProperties(KeycloakPolicyEnforcerProperties.class)
public class KeycloakAuthzAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public KeycloakAuthzAutoConfiguration() {}

  /**
   * The policy enforcer, configured from {@code codehunters.security.authz}.
   *
   * @param security Keycloak URL and realm
   * @param properties the catalog and enforcement settings
   * @return the enforcer filter
   */
  @Bean
  @ConditionalOnMissingBean
  public KeycloakPolicyEnforcerFilter keycloakPolicyEnforcerFilter(
      KeycloakSecurityProperties security, KeycloakPolicyEnforcerProperties properties) {
    return new KeycloakPolicyEnforcerFilter(
        PolicyEnforcerConfigs.from(security, properties), httpClient(properties));
  }

  /**
   * Keeps the enforcer out of the servlet container's filters: it runs only inside the security
   * chain.
   *
   * @param filter the enforcer filter
   * @return a disabled registration
   */
  @Bean
  public FilterRegistrationBean<KeycloakPolicyEnforcerFilter>
      keycloakPolicyEnforcerFilterRegistration(KeycloakPolicyEnforcerFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  /**
   * Adds the enforcer after Spring Security's authorization filter.
   *
   * @param filter the enforcer filter
   * @return the customizer Spring Security applies to each {@link HttpSecurity}
   */
  @Bean
  public Customizer<HttpSecurity> keycloakPolicyEnforcerCustomizer(
      KeycloakPolicyEnforcerFilter filter) {
    return http -> http.addFilterAfter(filter, AuthorizationFilter.class);
  }

  // Keycloak's enforcer only accepts an Apache HttpClient 4; its default one has no timeouts and
  // two connections per route, too few for one authorization call per request.
  private static HttpClient httpClient(KeycloakPolicyEnforcerProperties properties) {
    var requestConfig =
        RequestConfig.custom()
            .setConnectTimeout(millis(properties.connectTimeout()))
            .setConnectionRequestTimeout(millis(properties.connectTimeout()))
            .setSocketTimeout(millis(properties.readTimeout()))
            .build();
    return HttpClients.custom()
        .setDefaultRequestConfig(requestConfig)
        .setMaxConnTotal(properties.maxConnections())
        .setMaxConnPerRoute(properties.maxConnections())
        .build();
  }

  private static int millis(Duration duration) {
    return Math.toIntExact(duration.toMillis());
  }
}
