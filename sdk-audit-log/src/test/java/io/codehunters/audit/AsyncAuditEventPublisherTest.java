package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class AsyncAuditEventPublisherTest {

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

  private static final Executor CALLER_THREAD = Runnable::run;
  private static final Executor SATURATED =
      task -> {
        throw new RejectedExecutionException("queue full");
      };

  private final List<AuditEvent> delivered = new ArrayList<>();
  private final List<AuditEvent> rejected = new ArrayList<>();

  @Test
  void should_hand_event_to_delegate_through_executor() {
    var publisher = new AsyncAuditEventPublisher(delivered::add, CALLER_THREAD, rejected::add);

    publisher.publish(EVENT);

    assertThat(delivered).containsExactly(EVENT);
  }

  @Test
  void should_report_event_when_executor_rejects_it() {
    var publisher = new AsyncAuditEventPublisher(delivered::add, SATURATED, rejected::add);

    publisher.publish(EVENT);

    assertThat(rejected).containsExactly(EVENT);
    assertThat(delivered).isEmpty();
  }

  @Test
  void should_not_fail_caller_when_executor_rejects_event() {
    var publisher = new AsyncAuditEventPublisher(delivered::add, SATURATED, rejected::add);

    assertThatCode(() -> publisher.publish(EVENT)).doesNotThrowAnyException();
  }

  @Test
  void should_contain_delegate_failure_inside_the_task() {
    AuditEventPublisherPort failing =
        event -> {
          throw new IllegalStateException("sink down");
        };
    var publisher = new AsyncAuditEventPublisher(failing, CALLER_THREAD, rejected::add);

    assertThatCode(() -> publisher.publish(EVENT)).doesNotThrowAnyException();
  }
}
