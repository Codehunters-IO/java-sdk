package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.ClaimRequest;
import io.codehunters.messaging.OutboxDispatchTrigger;
import io.codehunters.messaging.OutboxMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Which rows a claim takes: IN_FLIGHT rows that lost their claim time are stale, and the commit
 * grace holds back fresh rows with a key so a late-committing earlier row of the key goes first.
 */
class ClaimEligibilityIT {

  private static final String ORIGIN = "sentinel-ms-test";
  private static final String TOPIC = "t-main";
  private static final Duration TTL = Duration.ofMinutes(5);
  private static final Duration GRACE = Duration.ofSeconds(2);
  private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

  private JdbcClient jdbcClient;
  private OutboxJdbcAdapter adapter;

  @BeforeEach
  void setUp() {
    DataSource dataSource = PostgresTestSupport.dataSource();
    PostgresTestSupport.truncate();
    jdbcClient = JdbcClient.create(dataSource);
    var settings =
        new OutboxAdapterSettings(
            ORIGIN, Clock.fixed(NOW, ZoneOffset.UTC), OutboxDispatchTrigger.none());
    adapter = new OutboxJdbcAdapter(dataSource, JsonMapper.builder().build(), settings);
  }

  @Test
  void should_claim_in_flight_row_when_its_claim_time_is_missing() {
    UUID orphan = save("A", "A1", NOW.minusSeconds(60));
    clearClaimTime(orphan);

    assertThat(payloads(claim(Duration.ZERO))).containsExactly("A1");
  }

  @Test
  void should_claim_later_row_of_key_when_earlier_row_has_no_claim_time() {
    UUID orphan = save("A", "A1", NOW.minusSeconds(60));
    save("A", "A2", NOW.minusSeconds(60));
    clearClaimTime(orphan);

    assertThat(payloads(claim(Duration.ZERO))).containsExactly("A1", "A2");
  }

  @Test
  void should_hold_back_keyed_row_when_it_occurred_within_commit_grace() {
    save("A", "A1", NOW.minusSeconds(1));

    assertThat(claim(GRACE)).isEmpty();
  }

  @Test
  void should_claim_keyed_row_when_it_occurred_before_commit_grace() {
    save("A", "A1", NOW.minus(GRACE));

    assertThat(payloads(claim(GRACE))).containsExactly("A1");
  }

  @Test
  void should_claim_unkeyed_row_when_it_occurred_within_commit_grace() {
    save(null, "N1", NOW);

    assertThat(payloads(claim(GRACE))).containsExactly("N1");
  }

  @Test
  void should_claim_older_rows_of_key_and_hold_back_fresh_one_when_grace_is_set() {
    save("A", "A1", NOW.minusSeconds(60));
    save("A", "A2", NOW);

    assertThat(payloads(claim(GRACE))).containsExactly("A1");
  }

  @Test
  void should_claim_fresh_keyed_row_when_commit_grace_is_zero() {
    save("A", "A1", NOW);

    assertThat(payloads(claim(Duration.ZERO))).containsExactly("A1");
  }

  private UUID save(String key, String payload, Instant occurredAt) {
    var id = UUID.randomUUID();
    adapter.save(
        OutboxMessage.<String>builder()
            .id(id)
            .topic(TOPIC)
            .key(key)
            .message(payload)
            .occurredAt(occurredAt)
            .build());
    return id;
  }

  // An IN_FLIGHT row without claimed_at is left behind by an operator or an older version.
  private void clearClaimTime(UUID id) {
    jdbcClient
        .sql(
            "UPDATE outbox_message SET status = 'IN_FLIGHT', claimed_at = NULL,"
                + " claimed_by = 'w-old' WHERE id = :id")
        .param("id", id)
        .update();
  }

  private List<OutboxMessage<String>> claim(Duration commitGrace) {
    return adapter.claim(new ClaimRequest(ORIGIN, "w1", 10, TTL, 10, commitGrace));
  }

  private static List<String> payloads(List<OutboxMessage<String>> claimed) {
    return claimed.stream().map(OutboxMessage::message).toList();
  }
}
