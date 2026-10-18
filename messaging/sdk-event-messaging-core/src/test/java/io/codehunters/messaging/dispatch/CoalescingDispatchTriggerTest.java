package io.codehunters.messaging.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class CoalescingDispatchTriggerTest {

  private static final int CONCURRENT_REQUESTS = 2_000;

  private ManualExecutor executor;
  private ScriptedDispatch dispatch;
  private CoalescingDispatchTrigger trigger;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    executor = new ManualExecutor();
    dispatch = new ScriptedDispatch();
    trigger = new CoalescingDispatchTrigger(executor, () -> dispatch);
    logAppender = new ListAppender<>();
    logAppender.start();
    triggerLogger().addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    triggerLogger().detachAppender(logAppender);
  }

  @Test
  void should_submit_one_drain_without_running_it_inline_when_requested_while_idle() {
    trigger.requestDispatch();

    assertThat(executor.submitted()).isEqualTo(1);
    assertThat(dispatch.runs()).isZero();
  }

  @Test
  void should_coalesce_requests_into_one_drain_and_one_rerun_when_drain_has_not_started() {
    trigger.requestDispatch();
    trigger.requestDispatch();
    trigger.requestDispatch();

    executor.runAll();

    assertThat(executor.submitted()).isEqualTo(1);
    assertThat(dispatch.runs()).isEqualTo(2);
  }

  @Test
  void should_rerun_once_without_submitting_again_when_requested_during_run() {
    dispatch.duringFirstRun(
        () -> {
          trigger.requestDispatch();
          trigger.requestDispatch();
        });
    trigger.requestDispatch();

    executor.runAll();

    assertThat(executor.submitted()).isEqualTo(1);
    assertThat(dispatch.runs()).isEqualTo(2);
  }

  @Test
  void should_rerun_when_claims_come_back_full() {
    dispatch.returns(true, true, false);
    trigger.requestDispatch();

    executor.runAll();

    assertThat(dispatch.runs()).isEqualTo(3);
  }

  @Test
  void should_stop_draining_full_claims_when_stopped_during_run() {
    dispatch.returns(true, true, false);
    dispatch.duringFirstRun(trigger::stop);
    trigger.requestDispatch();

    executor.runAll();

    assertThat(dispatch.runs()).isEqualTo(1);
  }

  @Test
  void should_skip_owed_rerun_when_stopped_during_run() {
    dispatch.duringFirstRun(
        () -> {
          trigger.requestDispatch();
          trigger.stop();
        });
    trigger.requestDispatch();

    executor.runAll();

    assertThat(dispatch.runs()).isEqualTo(1);
  }

  @Test
  void should_not_submit_drain_when_requested_after_stop() {
    trigger.stop();

    trigger.requestDispatch();

    assertThat(executor.submitted()).isZero();
  }

  @Test
  void should_drain_again_when_requested_after_stop_and_resume() {
    trigger.stop();
    trigger.resume();

    trigger.requestDispatch();
    executor.runAll();

    assertThat(dispatch.runs()).isEqualTo(1);
  }

  @Test
  void should_drain_again_when_resumed_after_stop_during_run() {
    dispatch.duringFirstRun(
        () -> {
          trigger.requestDispatch();
          trigger.stop();
        });
    trigger.requestDispatch();
    executor.runAll();
    trigger.resume();

    trigger.requestDispatch();
    executor.runAll();

    assertThat(dispatch.runs()).isEqualTo(2);
  }

  @Test
  void should_stop_draining_and_keep_interrupt_when_drain_thread_is_interrupted() {
    dispatch.returns(true, true, false);
    dispatch.duringFirstRun(() -> Thread.currentThread().interrupt());
    trigger.requestDispatch();

    executor.runAll();

    assertThat(Thread.interrupted()).isTrue();
    assertThat(dispatch.runs()).isEqualTo(1);
  }

  @Test
  void should_drain_again_when_requested_after_interrupted_drain() {
    dispatch.duringFirstRun(() -> Thread.currentThread().interrupt());
    trigger.requestDispatch();
    executor.runAll();
    Thread.interrupted();

    trigger.requestDispatch();
    executor.runAll();

    assertThat(dispatch.runs()).isEqualTo(2);
  }

  @Test
  void should_submit_a_new_drain_when_requested_after_previous_drain_finished() {
    trigger.requestDispatch();
    executor.runAll();

    trigger.requestDispatch();
    executor.runAll();

    assertThat(executor.submitted()).isEqualTo(2);
    assertThat(dispatch.runs()).isEqualTo(2);
  }

  @Test
  void should_return_to_idle_without_throwing_when_executor_rejects_drain() {
    executor.rejectNext();

    assertThatNoException().isThrownBy(trigger::requestDispatch);
    trigger.requestDispatch();

    assertThat(executor.submitted()).isEqualTo(1);
  }

  @Test
  void should_log_exception_class_name_only_when_dispatch_fails() {
    dispatch.failWith(new IllegalStateException("PII-SENTINEL"));
    trigger.requestDispatch();

    executor.runAll();

    assertThat(logAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(line -> line.contains("java.lang.IllegalStateException"))
        .noneMatch(line -> line.contains("PII-SENTINEL"));
    assertThat(logAppender.list).allMatch(event -> event.getThrowableProxy() == null);
  }

  @Test
  void should_accept_new_requests_when_previous_dispatch_failed() {
    dispatch.failWith(new IllegalStateException("db down"));
    trigger.requestDispatch();
    executor.runAll();

    trigger.requestDispatch();

    assertThat(executor.submitted()).isEqualTo(2);
  }

  @Test
  void should_accept_new_requests_when_drain_died_with_an_error() {
    dispatch.failWith(new AssertionError("fatal"));
    trigger.requestDispatch();
    assertThatThrownBy(executor::runAll).isInstanceOf(AssertionError.class);

    trigger.requestDispatch();

    assertThat(executor.submitted()).isEqualTo(2);
  }

  @Test
  void should_skip_run_when_dispatch_port_is_unavailable() {
    CoalescingDispatchTrigger withoutPort = new CoalescingDispatchTrigger(executor, () -> null);
    withoutPort.requestDispatch();

    executor.runAll();
    withoutPort.requestDispatch();

    assertThat(executor.submitted()).isEqualTo(2);
  }

  @Test
  void should_never_run_two_dispatches_at_once_when_requests_race() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(4);
    CoalescingDispatchTrigger raced = new CoalescingDispatchTrigger(pool, () -> dispatch);

    IntStream.range(0, CONCURRENT_REQUESTS).parallel().forEach(i -> raced.requestDispatch());
    pool.shutdown();

    assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    assertThat(dispatch.maxConcurrentRuns()).isEqualTo(1);
    assertThat(dispatch.runs()).isBetween(1, CONCURRENT_REQUESTS);
  }

  private static Logger triggerLogger() {
    return (Logger) LoggerFactory.getLogger(CoalescingDispatchTrigger.class);
  }
}
