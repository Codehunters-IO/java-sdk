package io.codehunters.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.security.config.FakeKeycloak;
import io.codehunters.security.config.KeycloakSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SessionIntrospectorTest {

  private final FakeKeycloak keycloak = new FakeKeycloak();

  @AfterEach
  void tearDown() {
    keycloak.close();
  }

  @Test
  void should_report_active_when_keycloak_says_active() {
    assertThat(introspector(false).isActive(keycloak.token())).isTrue();
  }

  @Test
  void should_report_inactive_when_session_revoked() {
    keycloak.sessionActive(false);

    assertThat(introspector(false).isActive(keycloak.token())).isFalse();
  }

  @Test
  void should_reuse_cached_result_when_same_token_checked_again() {
    var introspector = introspector(false);
    String token = keycloak.token();

    introspector.isActive(token);
    introspector.isActive(token);

    assertThat(keycloak.introspectionCalls()).isEqualTo(1);
  }

  @Test
  void should_reject_when_keycloak_fails_and_fail_open_disabled() {
    keycloak.introspectionFailsWith(500);

    assertThat(introspector(false).isActive(keycloak.token())).isFalse();
  }

  @Test
  void should_allow_when_keycloak_fails_and_fail_open_enabled() {
    keycloak.introspectionFailsWith(500);

    assertThat(introspector(true).isActive(keycloak.token())).isTrue();
  }

  @Test
  void should_not_cache_result_when_introspection_failed() {
    keycloak.introspectionFailsWith(500);
    var introspector = introspector(true);
    String token = keycloak.token();

    introspector.isActive(token);
    introspector.isActive(token);

    assertThat(keycloak.introspectionCalls()).isEqualTo(2);
  }

  private SessionIntrospector introspector(boolean failOpen) {
    var config =
        new KeycloakSecurityProperties.Introspection(
            true,
            FakeKeycloak.CLIENT_ID,
            "secret",
            Duration.ofSeconds(30),
            100,
            failOpen,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1));
    return new SessionIntrospector(
        RestClient.create(), keycloak.introspectionUri(), config, Clock.systemUTC());
  }
}
