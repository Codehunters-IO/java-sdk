package io.codehunters.messaging.jdbc.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.ClaimRequest;
import io.codehunters.messaging.OutboxBacklog;
import io.codehunters.messaging.OutboxDispatchStore;
import io.codehunters.messaging.OutboxMessage;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxBacklogGaugesTest {

  private static final String ORIGIN = "o1";
  private static final Instant START = Instant.parse("2026-10-14T10:00:00Z");

  private final FakeRepository repository = new FakeRepository();
  private final MutableClock clock = new MutableClock(START);
  private Gauge pending;
  private Gauge oldestAge;

  @BeforeEach
  void bind() {
    var registry = new SimpleMeterRegistry();
    new OutboxBacklogGauges(repository, ORIGIN, clock).bindTo(registry);
    pending = registry.get(OutboxBacklogGauges.PENDING).tag("origin", ORIGIN).gauge();
    oldestAge = registry.get(OutboxBacklogGauges.OLDEST_AGE).tag("origin", ORIGIN).gauge();
  }

  @Test
  void should_report_pending_count_and_oldest_age_when_scraped() {
    repository.backlog =
        Optional.of(new OutboxBacklog(4, Optional.of(START.minus(Duration.ofSeconds(90)))));

    assertThat(pending.value()).isEqualTo(4.0);
    assertThat(oldestAge.value()).isEqualTo(90.0);
  }

  @Test
  void should_report_zero_age_when_nothing_is_pending() {
    repository.backlog = Optional.of(new OutboxBacklog(0, Optional.empty()));

    assertThat(pending.value()).isZero();
    assertThat(oldestAge.value()).isZero();
  }

  @Test
  void should_report_zero_when_store_cannot_tell() {
    repository.backlog = Optional.empty();

    assertThat(pending.value()).isZero();
    assertThat(oldestAge.value()).isZero();
  }

  @Test
  void should_query_once_for_both_gauges_within_cache_ttl() {
    repository.backlog = Optional.of(new OutboxBacklog(2, Optional.of(START)));
    pending.value();
    oldestAge.value();
    clock.advance(OutboxFailedGauge.CACHE_TTL.minusSeconds(1));
    pending.value();

    assertThat(repository.queries).isEqualTo(1);
  }

  @Test
  void should_query_again_when_cache_ttl_elapsed() {
    repository.backlog = Optional.of(new OutboxBacklog(2, Optional.of(START)));
    pending.value();
    repository.backlog = Optional.of(new OutboxBacklog(7, Optional.of(START)));
    clock.advance(OutboxFailedGauge.CACHE_TTL);

    assertThat(pending.value()).isEqualTo(7.0);
    assertThat(repository.queries).isEqualTo(2);
  }

  private static final class FakeRepository implements OutboxDispatchStore {

    private Optional<OutboxBacklog> backlog = Optional.empty();
    private int queries;

    @Override
    public Optional<OutboxBacklog> backlog() {
      queries++;
      return backlog;
    }

    @Override
    public long countFailed() {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<OutboxMessage<String>> claim(ClaimRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<OutboxMessage<String>> markSendStarted(OutboxMessage<String> outboxMessage) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void update(OutboxMessage<String> outboxMessage) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int purgePublished(Instant cutoff, int batchSize) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int requeue(Collection<UUID> ids) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int requeueFailed(String topic, String key) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class MutableClock extends Clock {

    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    private void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
