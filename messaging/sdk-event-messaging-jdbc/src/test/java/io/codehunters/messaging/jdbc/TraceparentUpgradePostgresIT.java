package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.ClaimRequest;
import io.codehunters.messaging.OutboxMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Round-trips the stored trace context on a table upgraded with the 9.0 add script. */
class TraceparentUpgradePostgresIT {

  private static final String ORIGIN = "upgraded-ms-test";
  private static final String TRACEPARENT =
      "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

  private OutboxJdbcAdapter adapter;

  @BeforeEach
  void setUp() {
    PostgresTestSupport.truncateUpgraded();
    adapter =
        new OutboxJdbcAdapter(
            PostgresTestSupport.upgradedDataSource(), JsonMapper.builder().build(), ORIGIN);
  }

  @Test
  void should_return_stored_traceparent_when_table_was_upgraded_with_the_add_script() {
    adapter.save(
        OutboxMessage.<String>builder()
            .id(UUID.randomUUID())
            .topic("t")
            .key("k")
            .message("p")
            .occurredAt(Instant.now())
            .traceparent(TRACEPARENT)
            .build());

    var claimed = adapter.claim(new ClaimRequest(ORIGIN, "w1", 10, Duration.ofMinutes(5), 1));

    assertThat(claimed)
        .singleElement()
        .extracting(OutboxMessage::traceparent)
        .isEqualTo(TRACEPARENT);
  }
}
