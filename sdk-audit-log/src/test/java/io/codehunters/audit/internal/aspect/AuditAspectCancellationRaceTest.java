package io.codehunters.audit.internal.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.codehunters.audit.NoOpAuditContextProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Each subscription is recorded once, however its terminal signals race. */
class AuditAspectCancellationRaceTest {

  private static final Clock FIXED = Clock.fixed(TestConfig.NOW, ZoneOffset.UTC);
  private static final Map<String, Object> ORDER = Map.of("id", "ORD-1");
  private static final Duration SETTLE = Duration.ofMillis(300);
  private static final Duration RACE = Duration.ofMillis(5);

  private final InMemoryAuditEventPublisher published = new InMemoryAuditEventPublisher();

  @Test
  void should_record_one_cancelled_event_when_the_caller_times_out_a_slow_mono() {
    AsyncOrderService orders = proxied(Mono.never(), Flux.empty());

    orders.readOrder("ORD-1").timeout(RACE).onErrorResume(ex -> Mono.empty()).block();

    await().during(SETTLE).untilAsserted(() -> assertThat(published.getEvents()).hasSize(1));
    assertThat(published.getLastEvent().status()).isEqualTo("CANCELLED");
  }

  @Test
  void should_record_one_event_when_the_mono_completion_races_the_callers_timeout() {
    AsyncOrderService orders = proxied(Mono.just(ORDER).delayElement(RACE), Flux.empty());

    orders.readOrder("ORD-1").timeout(RACE).onErrorResume(ex -> Mono.empty()).block();

    await().during(SETTLE).untilAsserted(() -> assertThat(published.getEvents()).hasSize(1));
  }

  @Test
  void should_record_one_cancelled_event_when_the_caller_takes_part_of_the_flux() {
    AsyncOrderService orders = proxied(Mono.empty(), Flux.just("ORD-1", "ORD-2", "ORD-3"));

    orders.listOrders("CUST-1").take(2).blockLast();

    await().during(SETTLE).untilAsserted(() -> assertThat(published.getEvents()).hasSize(1));
    assertThat(published.getLastEvent().status()).isEqualTo("CANCELLED");
  }

  @Test
  void should_record_one_success_event_when_the_caller_takes_the_whole_flux() {
    AsyncOrderService orders = proxied(Mono.empty(), Flux.just("ORD-1", "ORD-2"));

    orders.listOrders("CUST-1").take(2).blockLast();

    await().during(SETTLE).untilAsserted(() -> assertThat(published.getEvents()).hasSize(1));
  }

  private AsyncOrderService proxied(Mono<Map<String, Object>> mono, Flux<String> flux) {
    var factory =
        new AspectJProxyFactory(new AsyncOrderService(new CompletableFuture<>(), mono, flux));
    factory.setProxyTargetClass(true);
    factory.addAspect(
        new AuditAspect(
            published, new NoOpAuditContextProvider(), new AuditAspect.Options(FIXED, false, 0)));
    return factory.getProxy();
  }
}
