package io.codehunters.security.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.keycloak.authorization.client.AuthzClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

class KeycloakAuthzAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(KeycloakAuthzAutoConfiguration.class));

  @Test
  void should_not_register_pep_when_not_enabled() {
    runner.run(
        context -> {
          assertThat(context).doesNotHaveBean(KeycloakAuthzFilter.class);
          assertThat(context).doesNotHaveBean(AuthzClient.class);
        });
  }

  // AuthzClient.create() fetches the Keycloak discovery document eagerly, so a real client cannot
  // be built without a running Keycloak; the PEP wiring is checked with a provided client.
  @Test
  void should_register_pep_when_enabled() {
    runner
        .withBean(AuthzClient.class, () -> mock(AuthzClient.class))
        .withPropertyValues(
            "keycloak.policy-enforcer.enabled=true",
            "keycloak.policy-enforcer.resource=svc",
            "keycloak.policy-enforcer.client-secret=secret")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AuthzClient.class);
              assertThat(context).hasSingleBean(KeycloakAuthzFilter.class);
              assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            });
  }
}
