package io.codehunters.audit.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditEventPublisherPort;
import io.codehunters.audit.AuditPersistenceException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FailSafeAuditEventPublisherTest {

  private static final AuditEvent EVENT =
      new AuditEvent(
          "ORDER",
          "ORD-1",
          "CREATE",
          "user-1",
          "USER",
          null,
          Map.of(),
          "SUCCESS",
          null,
          "order-service",
          OffsetDateTime.parse("2026-10-06T12:00:00Z"));

  private static final AuditEventPublisherPort FAILING =
      event -> {
        throw new AuditPersistenceException("insert failed", new IllegalStateException());
      };

  private final List<AuditEvent> failed = new ArrayList<>();
  private final List<AuditEvent> delivered = new ArrayList<>();

  @Test
  void should_not_throw_when_the_delegate_fails() {
    var publisher = new FailSafeAuditEventPublisher(FAILING, failed::add);

    assertThatCode(() -> publisher.publish(EVENT)).doesNotThrowAnyException();
  }

  @Test
  void should_report_the_event_when_the_delegate_fails() {
    var publisher = new FailSafeAuditEventPublisher(FAILING, failed::add);

    publisher.publish(EVENT);

    assertThat(failed).containsExactly(EVENT);
  }

  @Test
  void should_not_report_the_event_when_the_delegate_succeeds() {
    var publisher = new FailSafeAuditEventPublisher(delivered::add, failed::add);

    publisher.publish(EVENT);

    assertThat(delivered).containsExactly(EVENT);
    assertThat(failed).isEmpty();
  }
}
