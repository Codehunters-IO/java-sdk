package io.codehunters.messaging.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxFailedGaugeTest {

  private static final String ORIGIN = "o1";

  @Mock private OutboxRepositoryPort repository;

  @Test
  void should_report_failed_count_tagged_by_origin_when_bound() {
    when(repository.countFailed()).thenReturn(3L);
    var registry = new SimpleMeterRegistry();

    new OutboxFailedGauge(repository, ORIGIN).bindTo(registry);

    var gauge = registry.get("outbox_failed_messages").tag("origin", ORIGIN).gauge();
    assertThat(gauge.value()).isEqualTo(3.0);
  }
}
