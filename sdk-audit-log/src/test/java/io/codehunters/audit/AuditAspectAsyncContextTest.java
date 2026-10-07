package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Completion events carry the caller's context, not the state of the completing thread. */
class AuditAspectAsyncContextTest {

  private static final Clock FIXED = Clock.fixed(TestConfig.NOW, ZoneOffset.UTC);
  private static final Map<String, Object> ORDER = Map.of("id", "ORD-1");

  private final RoutingRecorder recorder = new RoutingRecorder();
  private final CompletableFuture<Map<String, Object>> pending = new CompletableFuture<>();

  @AfterEach
  void clearThreadState() {
    ThreadBoundContextProvider.clear();
    MDC.clear();
  }

  @Test
  void should_publish_completion_events_detached_from_the_completing_threads_transaction() {
    AsyncOrderService orders = proxied(Mono.empty());
    orders.placeOrderStage("ORD-1");

    pending.complete(ORDER);

    assertThat(recorder.detached()).hasSize(1);
    assertThat(recorder.attached()).isEmpty();
  }

  @Test
  void should_audit_a_declared_flux_subclass_when_the_method_returns() {
    AsyncOrderService orders = proxied(Mono.empty());

    orders.publishOrders("CUST-1");

    assertThat(recorder.attached()).extracting(AuditEvent::entityId).containsExactly("CUST-1");
  }

  @Test
  void should_take_the_actor_from_the_calling_thread() {
    AsyncOrderService orders = proxied(Mono.empty());
    ThreadBoundContextProvider.actAs("alice");
    orders.placeOrderStage("ORD-1");
    ThreadBoundContextProvider.clear();

    pending.complete(ORDER);

    assertThat(recorder.detached().getFirst().actorId()).isEqualTo("alice");
    assertThat(recorder.detached().getFirst().ipAddress()).isEqualTo("10.0.0.1");
  }

  @Test
  void should_resolve_argument_expressions_when_the_method_returns() {
    AsyncOrderService orders = proxied(Mono.empty());
    Map<String, Object> command = new HashMap<>(Map.of("id", "ORD-1"));
    orders.placeFor(command);
    command.put("id", "CHANGED-LATER");

    pending.complete(ORDER);

    assertThat(recorder.detached().getFirst().entityId()).isEqualTo("ORD-1");
  }

  @Test
  void should_resolve_result_expressions_when_the_work_completes() {
    AsyncOrderService orders = proxied(Mono.empty());
    orders.placeOrder("ignored");

    pending.complete(ORDER);

    assertThat(recorder.detached().getFirst().entityId()).isEqualTo("ORD-1");
  }

  @Test
  void should_publish_with_the_callers_mdc() throws InterruptedException {
    AsyncOrderService orders = proxied(Mono.empty());
    MDC.put(RoutingRecorder.TRACE_ID, "trace-1");
    orders.placeOrderStage("ORD-1");
    MDC.clear();

    pending.complete(ORDER);

    assertThat(recorder.next().traceId()).isEqualTo("trace-1");
  }

  @Test
  void should_restore_the_completing_threads_mdc_after_publishing() {
    AsyncOrderService orders = proxied(Mono.empty());
    MDC.put(RoutingRecorder.TRACE_ID, "trace-1");
    orders.placeOrderStage("ORD-1");
    MDC.put(RoutingRecorder.TRACE_ID, "trace-2");

    pending.complete(ORDER);

    assertThat(MDC.get(RoutingRecorder.TRACE_ID)).isEqualTo("trace-2");
  }

  @Test
  void should_not_publish_on_a_non_blocking_thread() throws InterruptedException {
    AsyncOrderService orders = proxied(Mono.just(ORDER).publishOn(Schedulers.parallel()));

    orders.readOrder("ORD-1").block();

    assertThat(recorder.next().nonBlockingThread()).isFalse();
  }

  private AsyncOrderService proxied(Mono<Map<String, Object>> mono) {
    var factory = new AspectJProxyFactory(new AsyncOrderService(pending, mono, Flux.just("ORD-1")));
    factory.setProxyTargetClass(true);
    factory.addAspect(
        new AuditAspect(
            recorder, new ThreadBoundContextProvider(), new AuditAspect.Options(FIXED, false, 0)));
    return factory.getProxy();
  }
}
