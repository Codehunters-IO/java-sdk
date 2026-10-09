package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.dispatch.SendOutboxMessagesUseCase;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Attempts are charged on reclaim only for rows whose send had started, against the reference DDL
 * of each supported database. Subclasses supply a clean data source loaded with that DDL.
 */
abstract class SendStartedReclaimContract {

  private static final String ORIGIN = "reclaim-test";
  private static final Duration TTL = Duration.ofMinutes(5);
  private static final int MAX_ATTEMPTS = 3;

  private JdbcClient jdbcClient;
  private OutboxJdbcAdapter adapter;
  private RecordingPublisher publisher;

  abstract DataSource dataSource();

  abstract void truncate();

  @BeforeEach
  void setUp() {
    truncate();
    jdbcClient = JdbcClient.create(dataSource());
    adapter = new OutboxJdbcAdapter(dataSource(), JsonMapper.builder().build(), ORIGIN);
    publisher = new RecordingPublisher();
  }

  @Test
  void should_charge_only_the_started_row_when_abandoned_batch_is_reclaimed() {
    UUID started = save("k1");
    UUID notSentA = save("k2");
    UUID notSentB = save("k3");
    var abandoned = adapter.claimPendingMessages(ORIGIN, 10, "dead", TTL);
    boolean marked = adapter.markSendStarted(byId(abandoned, started));
    ageClaims();

    adapter.claimPendingMessages(ORIGIN, 10, "live", TTL);

    assertThat(marked).isTrue();

    assertThat(attemptsOf(started)).isEqualTo(1);
    assertThat(attemptsOf(notSentA)).isZero();
    assertThat(attemptsOf(notSentB)).isZero();
  }

  @Test
  void should_mark_failed_without_publishing_when_started_row_reaches_max_attempts_on_reclaim() {
    UUID id = save("k1");
    setAttempts(id, MAX_ATTEMPTS - 1);
    var abandoned = adapter.claimPendingMessages(ORIGIN, 10, "dead", TTL);
    boolean marked = adapter.markSendStarted(byId(abandoned, id));
    ageClaims();

    useCase().execute();

    assertThat(marked).isTrue();
    assertThat(publisher.published).isEmpty();
    assertThat(rowOf(id))
        .containsEntry("status", "FAILED")
        .containsEntry("error", "max-attempts-exceeded")
        .containsEntry("attempts", MAX_ATTEMPTS);
  }

  @Test
  void should_refuse_send_mark_when_claim_was_taken_over_by_another_dispatcher() {
    UUID id = save("k1");
    var stale = adapter.claimPendingMessages(ORIGIN, 10, "dead", TTL).get(0);
    ageClaims();
    adapter.claimPendingMessages(ORIGIN, 10, "live", TTL);

    boolean marked = adapter.markSendStarted(stale);

    assertThat(marked).isFalse();
    assertThat(rowOf(id).get("send_started_at")).isNull();
  }

  @Test
  void should_publish_and_clear_send_mark_when_dispatch_succeeds() {
    UUID id = save("k1");

    useCase().execute();

    assertThat(publisher.published).extracting(OutboxMessage::getId).containsExactly(id);
    assertThat(rowOf(id)).containsEntry("status", "PUBLISHED").containsEntry("attempts", 0);
    assertThat(rowOf(id).get("send_started_at")).isNull();
  }

  @Test
  void should_schedule_retry_and_clear_send_mark_when_publish_fails() {
    UUID id = save("k1");

    useCase(new FailingPublisher()).execute();

    assertThat(rowOf(id)).containsEntry("status", "PENDING").containsEntry("attempts", 1);
    assertThat(rowOf(id).get("send_started_at")).isNull();
  }

  @Test
  void should_mark_failed_and_clear_send_mark_when_last_publish_attempt_fails() {
    UUID id = save("k1");
    setAttempts(id, MAX_ATTEMPTS - 1);

    useCase(new FailingPublisher()).execute();

    assertThat(rowOf(id)).containsEntry("status", "FAILED").containsEntry("attempts", MAX_ATTEMPTS);
    assertThat(rowOf(id).get("send_started_at")).isNull();
  }

  private SendOutboxMessagesUseCase useCase() {
    return useCase(publisher);
  }

  private SendOutboxMessagesUseCase useCase(MessagePublisherPort sender) {
    return new SendOutboxMessagesUseCase(
        adapter, sender, 10, MAX_ATTEMPTS, "live", ORIGIN, TTL.toMillis(), 1_000L, 60_000L);
  }

  private static OutboxMessage<String> byId(List<OutboxMessage<String>> messages, UUID id) {
    return messages.stream()
        .filter(message -> id.equals(message.getId()))
        .findFirst()
        .orElseThrow();
  }

  private UUID save(String key) {
    UUID id = UUID.randomUUID();
    adapter.save(
        OutboxMessage.<String>builder()
            .id(id)
            .topic("t")
            .key(key)
            .message("p")
            .occurredAt(LocalDateTime.now(ZoneOffset.UTC))
            .build());
    return id;
  }

  private void ageClaims() {
    jdbcClient
        .sql("UPDATE outbox_message SET claimed_at = :old WHERE status = 'IN_FLIGHT'")
        .param("old", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10))
        .update();
  }

  private void setAttempts(UUID id, int attempts) {
    jdbcClient
        .sql("UPDATE outbox_message SET attempts = :attempts WHERE id = :id")
        .param("attempts", attempts)
        .param("id", id)
        .update();
  }

  private Integer attemptsOf(UUID id) {
    return jdbcClient
        .sql("SELECT attempts FROM outbox_message WHERE id = :id")
        .param("id", id)
        .query(Integer.class)
        .single();
  }

  private Map<String, Object> rowOf(UUID id) {
    return jdbcClient
        .sql("SELECT status, error, attempts, send_started_at FROM outbox_message WHERE id = :id")
        .param("id", id)
        .query()
        .singleRow();
  }

  /** Publisher whose every send fails, as a broker outage would. */
  private static final class FailingPublisher implements MessagePublisherPort {

    @Override
    public void publish(String topic, String routingKey, String payload) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void publish(String topic, String payload) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void publish(OutboxMessage<String> message) {
      throw new IllegalStateException("broker unavailable");
    }
  }

  /** Publisher that records every message handed to it. */
  private static final class RecordingPublisher implements MessagePublisherPort {
    private final List<OutboxMessage<String>> published = new ArrayList<>();

    @Override
    public void publish(String topic, String routingKey, String payload) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void publish(String topic, String payload) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void publish(OutboxMessage<String> message) {
      published.add(message);
    }
  }
}
