package io.codehunters.tracing.mdc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.tracing.TraceHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

  private MdcTaskDecorator decorator;

  @BeforeEach
  void setUp() {
    decorator = new MdcTaskDecorator();
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  @DisplayName("should_propagate_mdc_to_task_thread_when_context_present")
  void should_propagate_mdc_when_context_present() {
    MDC.put("traceId", "abc");
    String[] captured = new String[1];
    Runnable inner = () -> captured[0] = MDC.get("traceId");

    decorator.decorate(inner).run();

    assertThat(captured[0]).isEqualTo("abc");
  }

  @Test
  @DisplayName("should_clear_mdc_in_task_thread_when_outer_context_empty")
  void should_clear_mdc_when_outer_empty() {
    String[] captured = new String[1];
    Runnable inner =
        () -> {
          MDC.put("dirty", "yes");
          captured[0] = MDC.get("traceId");
        };

    decorator.decorate(inner).run();

    assertThat(captured[0]).isNull();
  }

  @Test
  @DisplayName("should_restore_previous_mdc_after_task_runs")
  void should_restore_mdc_after_task() {
    MDC.put("outer", "before");
    Runnable inner = () -> MDC.put("inner", "leak");
    Runnable wrapped = decorator.decorate(inner);
    MDC.put("outer", "after-snapshot");

    wrapped.run();

    assertThat(MDC.get("outer")).isEqualTo("after-snapshot");
    assertThat(MDC.get("inner")).isNull();
  }

  @Test
  @DisplayName("should_clear_mdc_after_task_when_previous_was_empty")
  void should_clear_after_task_when_previous_empty() {
    Runnable wrapped = decorator.decorate(() -> MDC.put("inner", "leak"));

    wrapped.run();

    assertThat(MDC.get("inner")).isNull();
  }

  @Test
  void should_carry_trace_flags_to_task_thread() {
    MDC.put(TraceHeaders.MDC_TRACE_FLAGS, "00");
    String[] seenFlags = new String[1];
    Runnable task =
        new MdcTaskDecorator().decorate(() -> seenFlags[0] = MDC.get(TraceHeaders.MDC_TRACE_FLAGS));
    MDC.clear();

    task.run();

    assertThat(seenFlags[0]).isEqualTo("00");
  }
}
