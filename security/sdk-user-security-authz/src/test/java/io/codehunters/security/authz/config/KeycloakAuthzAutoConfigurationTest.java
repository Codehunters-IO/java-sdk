package io.codehunters.security.authz.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.security.authz.servlet.KeycloakPolicyEnforcerFilter;
import io.codehunters.security.config.KeycloakSecurityAutoConfiguration;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.ResolvableType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

class KeycloakAuthzAutoConfigurationTest {

  private static final String HTTP_CLIENT = "keycloakPolicyEnforcerHttpClient";

  // Nothing listens on port 1: the context must start without contacting Keycloak.
  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  KeycloakSecurityAutoConfiguration.class, KeycloakAuthzAutoConfiguration.class))
          .withPropertyValues(
              "codehunters.security.url=http://127.0.0.1:1",
              "codehunters.security.realm=test",
              "codehunters.security.audiences[0]=svc",
              "codehunters.security.introspection.enabled=false");

  private final WebApplicationContextRunner enabled =
      runner.withPropertyValues(
          "codehunters.security.authz.enabled=true",
          "codehunters.security.authz.resource=svc",
          "codehunters.security.authz.client-secret=secret");

  @Test
  void should_not_register_enforcer_when_not_enabled() {
    runner.run(
        context -> {
          assertThat(context).doesNotHaveBean(KeycloakPolicyEnforcerFilter.class);
          assertThat(context).doesNotHaveBean("keycloakPolicyEnforcerCustomizer");
        });
  }

  @Test
  void should_register_enforcer_without_contacting_keycloak_when_enabled() {
    enabled.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(KeycloakPolicyEnforcerFilter.class);
          assertThat(context).hasBean("keycloakPolicyEnforcerCustomizer");
        });
  }

  @Test
  void should_keep_enforcer_out_of_the_servlet_filters_when_enabled() {
    enabled.run(
        context -> assertThat(context.getBean(FilterRegistrationBean.class).isEnabled()).isFalse());
  }

  @Test
  void should_publish_customizer_that_spring_security_applies_to_http_security() {
    enabled.run(
        context ->
            assertThat(
                    context
                        .getBeanProvider(
                            ResolvableType.forClassWithGenerics(
                                Customizer.class, HttpSecurity.class))
                        .stream()
                        .count())
                .isEqualTo(1));
  }

  @Test
  void should_fail_startup_when_enabled_without_client_secret() {
    runner
        .withPropertyValues(
            "codehunters.security.authz.enabled=true", "codehunters.security.authz.resource=svc")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("codehunters.security.authz.client-secret"));
  }

  @Test
  void should_fail_startup_when_enforcement_mode_is_unknown() {
    enabled
        .withPropertyValues("codehunters.security.authz.enforcement-mode=LENIENT")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .hasStackTraceContaining("codehunters.security.authz.enforcement-mode"));
  }

  @Test
  void should_fail_startup_when_method_mapping_has_no_scopes() {
    enabled
        .withPropertyValues(
            "codehunters.security.authz.paths[0].path=/api/users/*",
            "codehunters.security.authz.paths[0].name=users",
            "codehunters.security.authz.paths[0].methods[0].method=GET")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("scopes must not be empty"));
  }

  @Test
  void should_close_keycloak_http_client_when_context_closes() {
    enabled.run(
        context -> {
          var client = context.getBean(HTTP_CLIENT, CloseableHttpClient.class);

          context.close();

          assertThatThrownBy(() -> client.execute(new HttpGet("http://127.0.0.1:1/")))
              .isInstanceOf(IllegalStateException.class);
        });
  }

  @Test
  void should_keep_keycloak_http_client_out_of_injection_by_type() {
    enabled.run(
        context -> {
          assertThat(context).hasBean(HTTP_CLIENT);
          assertThat(context.getBeanProvider(HttpClient.class).getIfAvailable()).isNull();
        });
  }

  @Test
  void should_keep_application_enforcer_when_one_is_defined() {
    var custom = new KeycloakPolicyEnforcerFilter(new PolicyEnforcerConfig(), null);
    enabled
        .withBean("customEnforcer", KeycloakPolicyEnforcerFilter.class, () -> custom)
        .run(
            context ->
                assertThat(context.getBean(KeycloakPolicyEnforcerFilter.class)).isSameAs(custom));
  }
}
