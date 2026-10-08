package io.codehunters.messaging.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.domain.model.OutboxMessage;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxFailedGaugeTest {

  private static final String ORIGIN = "o1";
  private static final Instant START = Instant.parse("2026-10-05T10:00:00Z");

  private final FakeRepository repository = new FakeRepository();
  private final MutableClock clock = new MutableClock(START);
  private Gauge gauge;

  @BeforeEach
  void bind() {
    var registry = new SimpleMeterRegistry();
    new OutboxFailedGauge(repository, ORIGIN, clock).bindTo(registry);
    gauge = registry.get(OutboxFailedGauge.METRIC_NAME).tag("origin", ORIGIN).gauge();
  }

  @Test
  void should_report_failed_count_tagged_by_origin_when_scraped() {
    repository.failed = 3;

    assertThat(gauge.value()).isEqualTo(3.0);
  }

  @Test
  void should_reuse_cached_count_when_scraped_again_within_ttl() {
    repository.failed = 3;
    gauge.value();
    repository.failed = 8;
    clock.advance(OutboxFailedGauge.CACHE_TTL.minusSeconds(1));

    assertThat(gauge.value()).isEqualTo(3.0);
    assertThat(repository.queries).isEqualTo(1);
  }

  @Test
  void should_query_again_when_cache_ttl_elapsed() {
    repository.failed = 3;
    gauge.value();
    repository.failed = 8;
    clock.advance(OutboxFailedGauge.CACHE_TTL);

    assertThat(gauge.value()).isEqualTo(8.0);
    assertThat(repository.queries).isEqualTo(2);
  }

  @Test
  void should_not_query_repository_when_bound_but_never_scraped() {
    assertThat(repository.queries).isZero();
  }

  private static final class FakeRepository implements OutboxRepositoryPort {

    private long failed;
    private int queries;

    @Override
    public long countFailed() {
      queries++;
      return failed;
    }

    @Override
    public <T> void save(OutboxMessage<T> outboxMessage) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<OutboxMessage<String>> claimPendingMessages(
        String origin, int batchSize, String instanceId, Duration claimTtl) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean markSendStarted(OutboxMessage<String> outboxMessage) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void update(OutboxMessage<String> outboxMessage) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int purgePublished(LocalDateTime cutoff, int batchSize) {
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
