package io.codehunters.security.authz.config;

import io.codehunters.security.authz.servlet.KeycloakPolicyEnforcerFilter;
import io.codehunters.security.config.KeycloakSecurityAutoConfiguration;
import io.codehunters.security.config.KeycloakSecurityProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.keycloak.adapters.authorization.PolicyEnforcer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Adds Keycloak's official policy enforcer to the Spring Security filter chain. Opt-in per service
 * with {@code codehunters.security.authz.enabled=true}; each service supplies its own path catalog.
 *
 * <p>The enforcer is placed right after Spring Security's {@link AuthorizationFilter}, through a
 * {@code Customizer<HttpSecurity>} bean that Spring Security applies to every {@link HttpSecurity}
 * it builds: the sdk-user-security chains and any chain the application defines. Requests are
 * therefore authenticated (or rejected with 401) before Keycloak is asked. Only the token Spring
 * Security validated reaches Keycloak; requests authenticated without one are denied unless their
 * path is listed in {@code skip-paths}. A public path may not cover a catalogued one.
 *
 * <p>The enforcer is built on the first enforced request, so a service starts while Keycloak is
 * unreachable; a failed build is retried after {@code retry-backoff}. Calls to Keycloak go through
 * the {@code keycloakPolicyEnforcerHttpClient} bean: connect and read timeouts, a bounded
 * connection pool, closed with the context. With Micrometer and a {@code MeterRegistry}, failures
 * are counted as {@code codehunters.authz.enforcer.failures}.
 */
@AutoConfiguration(after = KeycloakSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({PolicyEnforcer.class, SecurityFilterChain.class})
@ConditionalOnBooleanProperty("codehunters.security.authz.enabled")
@ConditionalOnBean(KeycloakSecurityProperties.class)
@EnableConfigurationProperties(KeycloakPolicyEnforcerProperties.class)
public class KeycloakAuthzAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(KeycloakAuthzAutoConfiguration.class);
  private static final String HTTP_CLIENT = "keycloakPolicyEnforcerHttpClient";

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public KeycloakAuthzAutoConfiguration() {}

  /**
   * The policy enforcer, configured from {@code codehunters.security.authz}.
   *
   * @param security Keycloak URL and realm
   * @param properties the catalog and enforcement settings
   * @param httpClient the client for calls to Keycloak
   * @param counters the failure counter, present when Micrometer is
   * @return the enforcer filter
   */
  @Bean
  @ConditionalOnMissingBean
  public KeycloakPolicyEnforcerFilter keycloakPolicyEnforcerFilter(
      KeycloakSecurityProperties security,
      KeycloakPolicyEnforcerProperties properties,
      @Qualifier(HTTP_CLIENT) CloseableHttpClient httpClient,
      ObjectProvider<EnforcerFailureCounter> counters) {
    CatalogChecks.requireNoPublicPathCovers(security.publicPaths(), properties.paths());
    EnforcerFailureCounter counter = counters.getIfAvailable(EnforcerFailureCounter::none);
    var options =
        KeycloakPolicyEnforcerFilter.Options.defaults()
            .withSkipPaths(skipPaths(properties.skipPaths()))
            .withRetryBackoff(properties.retryBackoff())
            .withFailureListener(e -> counter.increment(EnforcerFailureCounter.ENFORCER));
    return new KeycloakPolicyEnforcerFilter(
        PolicyEnforcerConfigs.from(security, properties), httpClient, options);
  }

  /**
   * The client for calls to Keycloak, closed with the context. Not a default candidate, so it never
   * becomes the application's {@code HttpClient}.
   *
   * <p>Keycloak's enforcer only accepts an Apache HttpClient 4; its default one has no timeouts and
   * two connections per route, too few for one authorization call per request.
   *
   * @param properties timeouts and connection bound
   * @param counters the failure counter, present when Micrometer is
   * @return the client
   */
  @Bean(name = HTTP_CLIENT, defaultCandidate = false, destroyMethod = "close")
  public CloseableHttpClient keycloakPolicyEnforcerHttpClient(
      KeycloakPolicyEnforcerProperties properties,
      ObjectProvider<EnforcerFailureCounter> counters) {
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
        .setRetryHandler(reportingFailures(counters.getIfAvailable(EnforcerFailureCounter::none)))
        .build();
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

  // Keycloak's enforcer turns a failed authorization call into a denial (403) and logs it at debug
  // only; the client is where such failures can still be seen.
  private static HttpRequestRetryHandler reportingFailures(EnforcerFailureCounter counter) {
    return (exception, executionCount, context) -> {
      log.warn("Call to Keycloak failed: {}", exception.toString());
      counter.increment(EnforcerFailureCounter.CALL);
      return DefaultHttpRequestRetryHandler.INSTANCE.retryRequest(
          exception, executionCount, context);
    };
  }

  private static RequestMatcher skipPaths(List<String> patterns) {
    if (patterns.isEmpty()) {
      return request -> false;
    }
    var paths = PathPatternRequestMatcher.withDefaults();
    return new OrRequestMatcher(patterns.stream().<RequestMatcher>map(paths::matcher).toList());
  }

  private static int millis(Duration duration) {
    return Math.toIntExact(duration.toMillis());
  }

  /** Counts enforcer failures with Micrometer when it is on the classpath. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
  static class EnforcerMetricsConfiguration {

    @Bean
    EnforcerFailureCounter keycloakPolicyEnforcerFailureCounter(
        ObjectProvider<MeterRegistry> registries) {
      return new EnforcerFailureCounter(
          stage -> registries.ifAvailable(registry -> count(registry, stage)));
    }

    private static void count(MeterRegistry registry, String stage) {
      Counter.builder(EnforcerFailureCounter.METER)
          .description("Failures of the Keycloak policy enforcer")
          .tag("stage", stage)
          .register(registry)
          .increment();
    }
  }
}
