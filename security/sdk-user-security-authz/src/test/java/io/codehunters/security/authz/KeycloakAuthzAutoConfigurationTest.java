package io.codehunters.security.authz;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.security.KeycloakSecurityAutoConfiguration;
import io.codehunters.security.authz.KeycloakPolicyEnforcerProperties.EnforcementMode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

class KeycloakAuthzAutoConfigurationTest {

  // Nothing listens on port 1: the context must start without contacting Keycloak.
  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  KeycloakSecurityAutoConfiguration.class, KeycloakAuthzAutoConfiguration.class))
          .withPropertyValues(
              "codehunters.security.url=http://127.0.0.1:1",
              "codehunters.security.realm=test",
              "codehunters.security.introspection.enabled=false");

  @Test
  void should_not_register_pep_when_not_enabled() {
    runner.run(context -> assertThat(context).doesNotHaveBean(KeycloakAuthzFilter.class));
  }

  @Test
  void should_register_pep_without_contacting_keycloak_when_enabled() {
    runner
        .withPropertyValues(
            "codehunters.security.authz.enabled=true",
            "codehunters.security.authz.resource=svc",
            "codehunters.security.authz.client-secret=secret")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(KeycloakAuthzFilter.class);
              assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            });
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
  void should_keep_application_filter_when_one_is_defined() {
    var custom =
        new KeycloakAuthzFilter(
            () -> null,
            new KeycloakPolicyEnforcerProperties(true, EnforcementMode.ENFORCING, "svc", "s", List.of()));
    runner
        .withPropertyValues(
            "codehunters.security.authz.enabled=true",
            "codehunters.security.authz.resource=svc",
            "codehunters.security.authz.client-secret=secret")
        .withBean("customAuthzFilter", KeycloakAuthzFilter.class, () -> custom)
        .run(context -> assertThat(context.getBean(KeycloakAuthzFilter.class)).isSameAs(custom));
  }
}
