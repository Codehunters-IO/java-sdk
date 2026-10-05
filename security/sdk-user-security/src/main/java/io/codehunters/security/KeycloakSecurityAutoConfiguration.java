package io.codehunters.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Secures servlet applications with Keycloak access tokens verified locally: signature against the
 * realm's JWK set, issuer, expiry, optional audience, and, unless disabled, an introspection check
 * that the Keycloak session is still active. Gateway {@code x-user-*} headers are only accepted
 * when they agree with the token.
 *
 * <p>Runs before Spring Boot's security auto-configuration so its filter chain and decoder take
 * precedence; an application that defines its own {@link SecurityFilterChain} keeps it.
 */
@AutoConfiguration(
    beforeName = {
      "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
      "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration",
      "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
    })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, JwtDecoder.class})
@ConditionalOnBooleanProperty(name = "codehunters.security.enabled", matchIfMissing = true)
@EnableConfigurationProperties(KeycloakSecurityProperties.class)
public class KeycloakSecurityAutoConfiguration {

  /** JWK set fetches are rare and small; these bounds keep a slow realm from stalling requests. */
  private static final Duration JWKS_CONNECT_TIMEOUT = Duration.ofSeconds(2);

  private static final Duration JWKS_READ_TIMEOUT = Duration.ofSeconds(3);

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public KeycloakSecurityAutoConfiguration() {}

  /**
   * Introspector that rejects tokens of revoked Keycloak sessions.
   *
   * @param properties the security configuration
   * @param restClientBuilder the application's builder, when present
   * @return the introspector
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      name = "codehunters.security.introspection.enabled",
      matchIfMissing = true)
  public SessionIntrospector sessionIntrospector(
      KeycloakSecurityProperties properties, ObjectProvider<RestClient.Builder> restClientBuilder) {
    var introspection = properties.introspection();
    RestClient restClient =
        restClientBuilder
            .getIfAvailable(RestClient::builder)
            .requestFactory(
                requestFactory(introspection.connectTimeout(), introspection.readTimeout()))
            .build();
    return new SessionIntrospector(
        restClient, properties.introspectionUri(), introspection, Clock.systemUTC());
  }

  /**
   * Decoder that verifies the realm's tokens.
   *
   * @param properties the security configuration
   * @param introspector the session introspector, when enabled
   * @return the JWT decoder
   */
  @Bean
  @ConditionalOnMissingBean
  public JwtDecoder keycloakJwtDecoder(
      KeycloakSecurityProperties properties, ObjectProvider<SessionIntrospector> introspector) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
            .restOperations(
                new RestTemplate(requestFactory(JWKS_CONNECT_TIMEOUT, JWKS_READ_TIMEOUT)))
            .build();
    OAuth2TokenValidator<Jwt> claims = claimValidator(properties);
    SessionIntrospector sessions = introspector.getIfAvailable();
    decoder.setJwtValidator(
        sessions == null ? claims : new ActiveSessionValidator(claims, sessions));
    return decoder;
  }

  /**
   * Converter from verified tokens to {@link KeycloakPrincipal} authentications.
   *
   * @param properties the security configuration
   * @return the converter
   */
  @Bean
  @ConditionalOnMissingBean
  public KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter(
      KeycloakSecurityProperties properties) {
    return new KeycloakJwtAuthenticationConverter(properties.roleClients());
  }

  private static OAuth2TokenValidator<Jwt> claimValidator(KeycloakSecurityProperties properties) {
    List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
    validators.add(new JwtIssuerValidator(properties.issuer()));
    validators.add(
        new JwtClaimValidator<String>(JwtClaimNames.SUB, sub -> sub != null && !sub.isBlank()));
    if (!properties.audiences().isEmpty()) {
      validators.add(
          new JwtClaimValidator<List<String>>(
              JwtClaimNames.AUD,
              aud -> aud != null && aud.stream().anyMatch(properties.audiences()::contains)));
    }
    return JwtValidators.createDefaultWithValidators(validators);
  }

  private static SimpleClientHttpRequestFactory requestFactory(
      Duration connectTimeout, Duration readTimeout) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  /** Filter chains; skipped when the application defines its own {@link SecurityFilterChain}. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  @EnableWebSecurity
  static class FilterChainConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain keycloakExcludedPathsFilterChain(
        HttpSecurity http, KeycloakSecurityProperties properties) throws Exception {
      return http.securityMatcher(excluded(properties.excludedPaths()))
          .csrf(AbstractHttpConfigurer::disable)
          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
          .build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    SecurityFilterChain keycloakSecurityFilterChain(
        HttpSecurity http,
        KeycloakSecurityProperties properties,
        JwtDecoder decoder,
        KeycloakJwtAuthenticationConverter converter)
        throws Exception {
      return http.csrf(AbstractHttpConfigurer::disable)
          .cors(cors -> cors.configurationSource(corsSource(properties.cors())))
          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(
              authorize -> {
                if (!properties.publicPaths().isEmpty()) {
                  authorize
                      .requestMatchers(properties.publicPaths().toArray(String[]::new))
                      .permitAll();
                }
                authorize.anyRequest().authenticated();
              })
          .oauth2ResourceServer(
              server ->
                  server.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
          .addFilterAfter(new GatewayHeadersFilter(), BearerTokenAuthenticationFilter.class)
          .build();
    }

    private static RequestMatcher excluded(List<String> paths) {
      if (paths.isEmpty()) {
        return request -> false;
      }
      var matchers = PathPatternRequestMatcher.withDefaults();
      return new OrRequestMatcher(paths.stream().<RequestMatcher>map(matchers::matcher).toList());
    }

    private static CorsConfigurationSource corsSource(KeycloakSecurityProperties.Cors cors) {
      var configuration = new CorsConfiguration();
      configuration.setAllowedOrigins(cors.allowedOrigins());
      configuration.setAllowedMethods(cors.allowedMethods());
      configuration.setAllowedHeaders(cors.allowedHeaders());
      configuration.setAllowCredentials(cors.allowCredentials());
      configuration.setMaxAge(cors.maxAge());
      var source = new UrlBasedCorsConfigurationSource();
      source.registerCorsConfiguration("/**", configuration);
      return source;
    }
  }

  /** Enables {@code @PreAuthorize} and friends on application beans. */
  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity
  static class MethodSecurityConfiguration {}

  /** Lets controllers receive the {@link KeycloakPrincipal} through {@link CurrentPrincipal}. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(WebMvcConfigurer.class)
  static class CurrentPrincipalConfiguration {

    @Bean
    WebMvcConfigurer keycloakArgumentResolverConfigurer() {
      return new WebMvcConfigurer() {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
          resolvers.add(new CurrentPrincipalArgumentResolver());
        }
      };
    }
  }
}
