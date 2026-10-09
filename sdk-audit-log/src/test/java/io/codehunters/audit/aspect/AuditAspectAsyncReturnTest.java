package io.codehunters.audit.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.audit.AuditEventPublisherPort;
import io.codehunters.audit.AuditPersistenceException;
import io.codehunters.audit.NoOpAuditContextProvider;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class AuditAspectAsyncReturnTest {

  private static final Clock FIXED = Clock.fixed(TestConfig.NOW, ZoneOffset.UTC);
  private static final Map<String, Object> ORDER = Map.of("id", "ORD-1");

  private final InMemoryAuditEventPublisher published = new InMemoryAuditEventPublisher();
  private final CompletableFuture<Map<String, Object>> pending = new CompletableFuture<>();

  @Test
  void should_not_record_the_event_before_the_future_completes() {
    AsyncOrderService orders = proxied(Mono.empty(), Flux.empty());

    orders.placeOrder("ORD-1");

    assertThat(published.getEvents()).isEmpty();
  }

  @Test
  void should_record_success_with_the_result_when_the_future_completes() {
    AsyncOrderService orders = proxied(Mono.empty(), Flux.empty());
    orders.placeOrder("ORD-1");

    pending.complete(ORDER);

    assertThat(published.getLastEvent().status()).isEqualTo("SUCCESS");
    assertThat(published.getLastEvent().entityId()).isEqualTo("ORD-1");
  }

  @Test
  void should_return_the_methods_own_future_to_the_caller() {
    AsyncOrderService orders = proxied(Mono.empty(), Flux.empty());

    assertThat(orders.placeOrder("ORD-1")).isSameAs(pending);
  }

  @Test
  void should_record_the_exception_class_when_the_stage_fails() {
    AsyncOrderService orders = proxied(Mono.empty(), Flux.empty());
    orders.placeOrderStage("ORD-2");

    pending.completeExceptionally(new IllegalStateException("card 4111 declined"));

    assertThat(published.getLastEvent().success()).isFalse();
    assertThat(published.getLastEvent().errorMessage())
        .isEqualTo(IllegalStateException.class.getName());
  }

  @Test
  void should_record_the_wrapped_exception_class_when_the_stage_fails_with_completion_exception() {
    AsyncOrderService orders = proxied(Mono.empty(), Flux.empty());
    orders.placeOrderStage("ORD-3");

    pending.completeExceptionally(new CompletionException(new IllegalArgumentException()));

    assertThat(published.getLastEvent().errorMessage())
        .isEqualTo(IllegalArgumentException.class.getName());
  }

  @Test
  void should_record_success_when_the_method_returns_a_null_future() {
    AsyncOrderService orders = proxied(Mono.empty(), Flux.empty());

    orders.placeNothing("ORD-4");

    assertThat(published.getLastEvent().status()).isEqualTo("SUCCESS");
  }

  @Test
  void should_complete_the_callers_future_when_the_audit_write_fails_on_completion() {
    AuditEventPublisherPort failing =
        event -> {
          throw new AuditPersistenceException("insert failed", new IllegalStateException());
        };
    AsyncOrderService orders = proxied(failing, Mono.empty(), Flux.empty(), true);
    CompletableFuture<Map<String, Object>> returned = orders.placeOrder("ORD-5");

    pending.complete(ORDER);

    assertThat(returned).isCompletedWithValue(ORDER);
  }

  @Test
  void should_not_record_the_event_before_the_mono_is_subscribed() {
    AsyncOrderService orders = proxied(Mono.just(ORDER), Flux.empty());

    orders.readOrder("ORD-6");

    assertThat(published.getEvents()).isEmpty();
  }

  @Test
  void should_record_success_with_the_value_when_the_mono_emits() {
    AsyncOrderService orders = proxied(Mono.just(ORDER), Flux.empty());

    orders.readOrder("ORD-1").block();

    assertThat(published.getLastEvent().status()).isEqualTo("SUCCESS");
    assertThat(published.getLastEvent().entityId()).isEqualTo("ORD-1");
  }

  @Test
  void should_record_success_when_the_mono_completes_empty() {
    AsyncOrderService orders = proxied(Mono.empty(), Flux.empty());

    orders.readOrder("ORD-7").block();

    assertThat(published.getLastEvent().status()).isEqualTo("SUCCESS");
  }

  @Test
  void should_record_the_exception_class_when_the_mono_fails() {
    AsyncOrderService orders =
        proxied(Mono.error(new IllegalStateException("timeout")), Flux.empty());

    assertThatThrownBy(() -> orders.readOrder("ORD-8").block())
        .isInstanceOf(IllegalStateException.class);

    assertThat(published.getLastEvent().errorMessage())
        .isEqualTo(IllegalStateException.class.getName());
  }

  @Test
  void should_record_one_event_when_the_flux_completes() {
    AsyncOrderService orders = proxied(Mono.empty(), Flux.just("ORD-1", "ORD-2"));

    orders.listOrders("CUST-1").collectList().block();

    assertThat(published.getEvents()).hasSize(1);
    assertThat(published.getLastEvent().entityId()).isEqualTo("CUST-1");
  }

  @Test
  void should_record_the_exception_class_when_the_flux_fails() {
    AsyncOrderService orders =
        proxied(Mono.empty(), Flux.error(new IllegalArgumentException("bad page")));

    assertThatThrownBy(() -> orders.listOrders("CUST-2").blockLast())
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(published.getLastEvent().errorMessage())
        .isEqualTo(IllegalArgumentException.class.getName());
  }

  @Test
  void should_record_cancelled_when_the_future_is_cancelled() {
    AsyncOrderService orders = proxied(Mono.empty(), Flux.empty());
    orders.placeOrderStage("ORD-9");

    pending.cancel(false);

    assertThat(published.getLastEvent().status()).isEqualTo("CANCELLED");
    assertThat(published.getLastEvent().success()).isFalse();
    assertThat(published.getLastEvent().errorMessage())
        .isEqualTo(CancellationException.class.getName());
  }

  @Test
  void should_record_cancelled_when_the_subscriber_cancels_the_flux() {
    AsyncOrderService orders = proxied(Mono.empty(), Flux.just("ORD-1", "ORD-2"));

    orders.listOrders("CUST-3").next().block();

    assertThat(published.getEvents()).hasSize(1);
    assertThat(published.getLastEvent().status()).isEqualTo("CANCELLED");
  }

  @Test
  void should_record_cancelled_when_the_subscriber_cancels_the_mono() {
    AsyncOrderService orders = proxied(Mono.never(), Flux.empty());

    orders.readOrder("ORD-10").subscribe().dispose();

    assertThat(published.getLastEvent().status()).isEqualTo("CANCELLED");
  }

  @Test
  void should_record_one_event_per_subscription() {
    AsyncOrderService orders = proxied(Mono.empty(), Flux.just("ORD-1"));
    Flux<String> listed = orders.listOrders("CUST-4");

    listed.blockLast();
    listed.blockLast();

    assertThat(published.getEvents()).hasSize(2);
  }

  @Test
  void should_complete_the_callers_mono_when_publishing_the_completion_throws_an_error() {
    AuditEventPublisherPort failing =
        event -> {
          throw new LinkageError("serializer missing");
        };
    AsyncOrderService orders = proxied(failing, Mono.just(ORDER), Flux.empty(), false);

    assertThat(orders.readOrder("ORD-11").block()).isEqualTo(ORDER);
  }

  @Test
  void should_complete_the_callers_mono_when_the_audit_write_fails_with_fail_on_error() {
    AuditEventPublisherPort failing =
        event -> {
          throw new IllegalStateException("insert failed");
        };
    AsyncOrderService orders = proxied(failing, Mono.just(ORDER), Flux.empty(), true);

    assertThat(orders.readOrder("ORD-12").block()).isEqualTo(ORDER);
  }

  private AsyncOrderService proxied(Mono<Map<String, Object>> mono, Flux<String> flux) {
    return proxied(published, mono, flux, false);
  }

  private AsyncOrderService proxied(
      AuditEventPublisherPort publisher,
      Mono<Map<String, Object>> mono,
      Flux<String> flux,
      boolean failOnError) {
    var factory = new AspectJProxyFactory(new AsyncOrderService(pending, mono, flux));
    factory.setProxyTargetClass(true);
    factory.addAspect(
        new AuditAspect(
            publisher,
            new NoOpAuditContextProvider(),
            new AuditAspect.Options(FIXED, failOnError, 0)));
    return factory.getProxy();
  }
}
