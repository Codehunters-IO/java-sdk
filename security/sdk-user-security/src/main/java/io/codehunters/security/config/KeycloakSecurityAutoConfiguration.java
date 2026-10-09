package io.codehunters.security.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.jwk.source.RateLimitReachedException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.codehunters.security.gateway.GatewayHeadersFilter;
import io.codehunters.security.jwt.KeycloakJwtAuthenticationConverter;
import io.codehunters.security.mvc.CurrentPrincipalArgumentResolver;
import io.codehunters.security.session.ActiveSessionValidator;
import io.codehunters.security.session.SessionIntrospector;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
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
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.client.RestClient;
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
      "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration",
      "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web"
          + ".OAuth2ResourceServerWebSecurityAutoConfiguration",
      "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
      "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"
    })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, JwtDecoder.class})
@ConditionalOnBooleanProperty(name = "codehunters.security.enabled", matchIfMissing = true)
@EnableConfigurationProperties(KeycloakSecurityProperties.class)
public class KeycloakSecurityAutoConfiguration {

  /** JWK set fetches are rare and small; these bounds keep a slow realm from stalling requests. */
  private static final int JWKS_CONNECT_TIMEOUT_MILLIS = 2_000;

  private static final int JWKS_READ_TIMEOUT_MILLIS = 3_000;
  private static final int JWKS_SIZE_LIMIT_BYTES = 50 * 1024;
  // Keycloak marks access tokens with typ=Bearer; ID and refresh tokens carry other types.
  private static final String TOKEN_TYPE = "typ";
  private static final String ACCESS_TOKEN_TYPE = "Bearer";

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
    NimbusJwtDecoder decoder = new NimbusJwtDecoder(rs256Processor(properties.jwkSetUri()));
    OAuth2TokenValidator<Jwt> claims = claimValidator(properties);
    SessionIntrospector sessions = introspector.getIfAvailable();
    decoder.setJwtValidator(
        sessions == null ? claims : new ActiveSessionValidator(claims, sessions));
    return decoder;
  }

  /**
   * Converter from verified tokens to {@link io.codehunters.security.KeycloakPrincipal}
   * authentications.
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

  /**
   * RS256 only, with Nimbus' cached and rate-limited key source: a token naming an unknown key id
   * cannot make the service refetch the key set on every request. Claims are checked by Spring's
   * validators, so Nimbus' own claim verification is switched off.
   */
  private static DefaultJWTProcessor<SecurityContext> rs256Processor(String jwkSetUri) {
    JWKSource<SecurityContext> keys =
        JWKSourceBuilder.<SecurityContext>create(
                url(jwkSetUri),
                new DefaultResourceRetriever(
                    JWKS_CONNECT_TIMEOUT_MILLIS, JWKS_READ_TIMEOUT_MILLIS, JWKS_SIZE_LIMIT_BYTES))
            .build();
    var processor = new DefaultJWTProcessor<SecurityContext>();
    // A rate-limited refetch means "no such key": the token is rejected with 401, not a 500.
    JWSVerificationKeySelector<SecurityContext> selector =
        new JWSVerificationKeySelector<>(
            JWSAlgorithm.RS256,
            (jwkSelector, context) -> {
              try {
                return keys.get(jwkSelector, context);
              } catch (RateLimitReachedException e) {
                return List.of();
              }
            });
    processor.setJWSKeySelector(selector);
    processor.setJWTClaimsSetVerifier((claims, context) -> {});
    return processor;
  }

  private static java.net.URL url(String value) {
    try {
      return URI.create(value).toURL();
    } catch (MalformedURLException | IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "codehunters.security.jwk-set-uri is not a URL: " + value, e);
    }
  }

  private static OAuth2TokenValidator<Jwt> claimValidator(KeycloakSecurityProperties properties) {
    List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
    validators.add(new JwtIssuerValidator(properties.issuerUri()));
    validators.add(
        new JwtClaimValidator<String>(JwtClaimNames.SUB, sub -> sub != null && !sub.isBlank()));
    if (properties.requireAccessTokenType()) {
      validators.add(new JwtClaimValidator<String>(TOKEN_TYPE, ACCESS_TOKEN_TYPE::equals));
    }
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

    private static final Logger LOG = LoggerFactory.getLogger(FilterChainConfiguration.class);

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
        KeycloakJwtAuthenticationConverter converter,
        ObjectProvider<AuthenticationEntryPoint> entryPoints,
        ObjectProvider<AccessDeniedHandler> deniedHandlers)
        throws Exception {
      var entryPoint = single(entryPoints, AuthenticationEntryPoint.class);
      var denied = single(deniedHandlers, AccessDeniedHandler.class);
      respondWith(http, entryPoint, denied);
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
              server -> {
                server.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter));
                respondWith(server, entryPoint, denied);
              })
          .addFilterAfter(
              new GatewayHeadersFilter(entryPoint), BearerTokenAuthenticationFilter.class)
          .build();
    }

    // A single entry point / access-denied handler bean, or the primary one (sdk-web-errors offers
    // problem-detail ones that keep the bearer challenge) answers 401 and 403; without one,
    // Spring's
    // defaults apply. Several without a primary are reported, since the defaults then apply too.
    private static <T> T single(ObjectProvider<T> beans, Class<T> type) {
      var unique = beans.getIfUnique();
      var count = beans.stream().count();
      if (unique == null && count > 1) {
        LOG.warn(
            "{} {} beans and none is @Primary; Spring's default is used for the Keycloak chain",
            count,
            type.getSimpleName());
      }
      return unique;
    }

    private static void respondWith(
        HttpSecurity http, AuthenticationEntryPoint entryPoint, AccessDeniedHandler denied)
        throws Exception {
      http.exceptionHandling(
          handling -> {
            if (entryPoint != null) {
              handling.authenticationEntryPoint(entryPoint);
            }
            if (denied != null) {
              handling.accessDeniedHandler(denied);
            }
          });
    }

    private static void respondWith(
        OAuth2ResourceServerConfigurer<HttpSecurity> server,
        AuthenticationEntryPoint entryPoint,
        AccessDeniedHandler denied) {
      if (entryPoint != null) {
        server.authenticationEntryPoint(entryPoint);
      }
      if (denied != null) {
        server.accessDeniedHandler(denied);
      }
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

  /**
   * Enables {@code @PreAuthorize} and friends on application beans. Applications with their own
   * {@code @EnableMethodSecurity} settings turn this off with {@code
   * codehunters.security.method-security.enabled=false}.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnBooleanProperty(
      name = "codehunters.security.method-security.enabled",
      matchIfMissing = true)
  @EnableMethodSecurity
  static class MethodSecurityConfiguration {}

  /**
   * Lets controllers receive the {@link io.codehunters.security.KeycloakPrincipal} through {@link
   * CurrentPrincipal}.
   */
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
