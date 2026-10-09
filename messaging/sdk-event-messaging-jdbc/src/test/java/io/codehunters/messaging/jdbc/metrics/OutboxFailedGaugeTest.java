package io.codehunters.messaging.jdbc.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.ClaimRequest;
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

  private static final class FakeRepository implements OutboxDispatchStore {

    private long failed;
    private int queries;

    @Override
    public long countFailed() {
      queries++;
      return failed;
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
    public int requeueFailed(String key) {
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
