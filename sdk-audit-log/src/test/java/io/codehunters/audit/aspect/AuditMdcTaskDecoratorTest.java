package io.codehunters.audit.aspect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class AuditMdcTaskDecoratorTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  private final AuditMdcTaskDecorator decorator = new AuditMdcTaskDecorator();
  private final Map<String, String> seen = new HashMap<>();

  @BeforeEach
  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void should_run_task_with_the_submitting_thread_mdc() {
    MDC.put("traceId", TRACE_ID);
    Runnable task = decorator.decorate(() -> seen.put("traceId", MDC.get("traceId")));
    MDC.clear();

    task.run();

    assertThat(seen).containsEntry("traceId", TRACE_ID);
  }

  @Test
  void should_restore_the_worker_mdc_after_the_task() {
    MDC.put("traceId", TRACE_ID);
    Runnable task = decorator.decorate(() -> {});
    MDC.clear();
    MDC.put("worker", "audit-1");

    task.run();

    assertThat(MDC.getCopyOfContextMap()).isEqualTo(Map.of("worker", "audit-1"));
  }

  @Test
  void should_run_task_with_empty_mdc_when_submitter_had_none() {
    Runnable task = decorator.decorate(() -> seen.put("traceId", MDC.get("traceId")));
    MDC.put("traceId", "stale");

    task.run();

    assertThat(seen).containsEntry("traceId", null);
    assertThat(MDC.get("traceId")).isEqualTo("stale");
  }
}
