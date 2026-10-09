package io.codehunters.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.security.config.FakeKeycloak;
import io.codehunters.security.config.KeycloakSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SessionIntrospectorTest {

  private static final Duration TTL = Duration.ofSeconds(30);
  private static final int MAX_ENTRIES = 2;

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

  @Test
  void should_ask_keycloak_again_when_the_cache_ttl_elapsed() {
    var clock = new MutableClock();
    var introspector = introspector(MAX_ENTRIES, clock);
    String token = keycloak.token();

    introspector.isActive(token);
    clock.advance(TTL);
    introspector.isActive(token);

    assertThat(keycloak.introspectionCalls()).isEqualTo(2);
  }

  @Test
  void should_reuse_the_result_when_the_cache_ttl_has_not_elapsed() {
    var clock = new MutableClock();
    var introspector = introspector(MAX_ENTRIES, clock);
    String token = keycloak.token();

    introspector.isActive(token);
    clock.advance(TTL.minusSeconds(1));
    introspector.isActive(token);

    assertThat(keycloak.introspectionCalls()).isEqualTo(1);
  }

  @Test
  void should_keep_at_most_cache_max_entries_results_when_tokens_differ() {
    var introspector = introspector(MAX_ENTRIES, new MutableClock());

    introspector.isActive(keycloak.token(claims -> claims.subject("user-a")));
    introspector.isActive(keycloak.token(claims -> claims.subject("user-b")));
    introspector.isActive(keycloak.token(claims -> claims.subject("user-c")));
    introspector.cache().cleanUp();

    assertThat(introspector.cache().estimatedSize()).isLessThanOrEqualTo(MAX_ENTRIES);
  }

  @Test
  void should_record_hits_and_misses_when_the_same_token_is_checked_twice() {
    var introspector = introspector(false);
    String token = keycloak.token();

    introspector.isActive(token);
    introspector.isActive(token);

    assertThat(introspector.cache().stats().missCount()).isEqualTo(1);
    assertThat(introspector.cache().stats().hitCount()).isEqualTo(1);
  }

  @Test
  void should_count_failures_when_keycloak_cannot_answer() {
    keycloak.introspectionFailsWith(503);
    var introspector = introspector(true);

    introspector.isActive(keycloak.token());

    assertThat(introspector.failures()).isEqualTo(1);
  }

  @Test
  void should_count_no_failure_when_keycloak_answers() {
    var introspector = introspector(false);

    introspector.isActive(keycloak.token());

    assertThat(introspector.failures()).isZero();
  }

  private SessionIntrospector introspector(boolean failOpen) {
    return new SessionIntrospector(
        RestClient.create(), keycloak.introspectionUri(), config(100, failOpen), Clock.systemUTC());
  }

  private SessionIntrospector introspector(int maxEntries, Clock clock) {
    return new SessionIntrospector(
        RestClient.create(), keycloak.introspectionUri(), config(maxEntries, false), clock);
  }

  private static KeycloakSecurityProperties.Introspection config(int maxEntries, boolean failOpen) {
    return new KeycloakSecurityProperties.Introspection(
        true,
        FakeKeycloak.CLIENT_ID,
        "secret",
        TTL,
        maxEntries,
        failOpen,
        Duration.ofSeconds(1),
        Duration.ofSeconds(1));
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-05T12:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
