package io.codehunters.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

class KeycloakSecurityAutoConfigurationTest {

  private final FakeKeycloak keycloak = new FakeKeycloak();

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(KeycloakSecurityAutoConfiguration.class))
          .withPropertyValues(
              "codehunters.security.url=" + keycloak.url(),
              "codehunters.security.realm=" + FakeKeycloak.REALM,
              "codehunters.security.audiences[0]=" + FakeKeycloak.CLIENT_ID,
              "codehunters.security.introspection.client-id=" + FakeKeycloak.CLIENT_ID,
              "codehunters.security.introspection.client-secret=secret");

  @AfterEach
  void tearDown() {
    keycloak.close();
  }

  @Test
  void should_register_decoder_introspector_and_filter_chains_when_configured() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(JwtDecoder.class);
          assertThat(context).hasSingleBean(SessionIntrospector.class);
          assertThat(context).hasSingleBean(KeycloakJwtAuthenticationConverter.class);
          assertThat(context).hasBean("keycloakSecurityFilterChain");
          assertThat(context).hasBean("keycloakExcludedPathsFilterChain");
        });
  }

  @Test
  void should_not_register_introspector_when_introspection_disabled() {
    runner
        .withPropertyValues(
            "codehunters.security.introspection.enabled=false",
            "codehunters.security.introspection.client-id=",
            "codehunters.security.introspection.client-secret=")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(SessionIntrospector.class);
              assertThat(context).hasSingleBean(JwtDecoder.class);
            });
  }

  @Test
  void should_fail_startup_when_realm_is_missing() {
    runner
        .withPropertyValues("codehunters.security.realm=")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("codehunters.security.realm"));
  }

  @Test
  void should_fail_startup_when_no_audience_is_configured() {
    runner
        .withPropertyValues("codehunters.security.audiences=")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("codehunters.security.audiences"));
  }

  @Test
  void should_keep_keycloak_chain_when_actuator_security_is_present() {
    runner
        .withConfiguration(
            AutoConfigurations.of(
                ManagementWebSecurityAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasBean("keycloakSecurityFilterChain");
              assertThat(context).doesNotHaveBean("managementSecurityFilterChain");
            });
  }

  @Test
  void should_leave_method_security_to_the_application_when_disabled() {
    runner
        .withPropertyValues("codehunters.security.method-security.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .doesNotHaveBean(AuthorizationManagerBeforeMethodInterceptor.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("codehunters.security.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(JwtDecoder.class);
              assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
            });
  }

  @Test
  void should_register_nothing_when_spring_security_is_absent() {
    runner
        .withClassLoader(new FilteredClassLoader(SecurityFilterChain.class))
        .run(context -> assertThat(context).doesNotHaveBean(JwtDecoder.class));
  }

  @Test
  void should_keep_application_filter_chain_when_one_is_defined() {
    runner
        .withUserConfiguration(ApplicationSecurity.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(SecurityFilterChain.class);
              assertThat(context).hasBean("applicationFilterChain");
              assertThat(context).hasSingleBean(JwtDecoder.class);
            });
  }

  @Test
  void should_win_over_boot_security_and_resource_server_defaults() {
    runner
        .withConfiguration(
            AutoConfigurations.of(
                SecurityAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ResourceServerWebSecurityAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(JwtDecoder.class);
              assertThat(context).hasBean("keycloakSecurityFilterChain");
              assertThat(context).doesNotHaveBean("defaultSecurityFilterChain");
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebSecurity
  static class ApplicationSecurity {
    @Bean
    SecurityFilterChain applicationFilterChain(HttpSecurity http) throws Exception {
      return http.build();
    }
  }
}
