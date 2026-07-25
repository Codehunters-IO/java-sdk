package io.codehunters.sdk.security;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot auto-configuration wiring the Keycloak header-based security model: stateless filter
 * chain, CORS, method security and the {@link KeycloakSecurityFilter}. Active in {@code develop},
 * {@code cert} and {@code prod} profiles for servlet applications.
 */
@AutoConfiguration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(KeycloakSecurityProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Profile({"develop", "cert", "prod"})
public class KeycloakSecurityAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public KeycloakSecurityAutoConfiguration() {}

  /**
   * Builds the stateless security filter chain with CORS, public/excluded path rules and the {@link
   * KeycloakSecurityFilter} installed before the default authentication filter.
   *
   * @param http the HTTP security builder
   * @param jsonMapper the JSON mapper used to decode JWT payloads
   * @param properties the security configuration
   * @param sessionIntrospector optional provider used to validate/introspect the session, when
   *     present
   * @return the configured {@link SecurityFilterChain}
   * @throws Exception if the chain cannot be built
   */
  @Bean
  public SecurityFilterChain keycloakSecurityFilterChain(
      HttpSecurity http,
      JsonMapper jsonMapper,
      KeycloakSecurityProperties properties,
      ObjectProvider<SessionIntrospector> sessionIntrospector)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize -> {
              if (!properties.getExcludedPaths().isEmpty()) {
                authorize
                    .requestMatchers(properties.getExcludedPaths().toArray(String[]::new))
                    .permitAll();
              }
              if (!properties.getPublicPaths().isEmpty()) {
                authorize
                    .requestMatchers(properties.getPublicPaths().toArray(String[]::new))
                    .permitAll();
              }
              authorize.anyRequest().authenticated();
            })
        .addFilterBefore(
            new KeycloakSecurityFilter(
                jsonMapper, properties.getExcludedPaths(), sessionIntrospector.getIfAvailable()),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  /**
   * Introspector that validates access tokens against Keycloak per request, closing the
   * single-active-session gap by rejecting tokens whose Keycloak session was revoked. Always active
   * — session validation is mandatory by policy.
   *
   * @param jsonMapper the JSON mapper used to parse the introspection response
   * @param keycloakProperties the Keycloak introspection configuration
   * @return the session introspector
   */
  @Bean
  public SessionIntrospector sessionIntrospector(
      JsonMapper jsonMapper, KeycloakSecurityProperties keycloakProperties) {
    return new SessionIntrospector(RestClient.create(), jsonMapper, keycloakProperties);
  }

  /**
   * Registers {@link CurrentPrincipalArgumentResolver} so controllers can inject the current {@link
   * KeycloakPrincipal} via {@link CurrentPrincipal}.
   *
   * @return a Web MVC configurer contributing the resolver
   */
  @Bean
  public WebMvcConfigurer keycloakArgumentResolverConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentPrincipalArgumentResolver());
      }
    };
  }

  private CorsConfigurationSource corsConfigurationSource(KeycloakSecurityProperties properties) {
    KeycloakSecurityProperties.Cors corsProps = properties.getCors();
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsProps.getAllowedOrigins());
    configuration.setAllowedMethods(corsProps.getAllowedMethods());
    configuration.setAllowedHeaders(corsProps.getAllowedHeaders());
    configuration.setAllowCredentials(corsProps.isAllowCredentials());
    configuration.setMaxAge(corsProps.getMaxAge());
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
