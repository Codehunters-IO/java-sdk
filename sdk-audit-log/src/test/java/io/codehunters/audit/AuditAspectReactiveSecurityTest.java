package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * In WebFlux the authenticated user travels in the subscriber's Reactor context, not in a
 * thread-local, so a provider backed by a thread-bound security context finds no actor.
 */
class AuditAspectReactiveSecurityTest {

  private static final Clock FIXED = Clock.fixed(TestConfig.NOW, ZoneOffset.UTC);
  private static final Map<String, Object> ORDER = Map.of("id", "ORD-1");
  private static final Authentication ALICE = user("alice");
  private static final Authentication BOB = user("bob");
  private static final Authentication ANONYMOUS =
      new AnonymousAuthenticationToken(
          "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

  private final RoutingRecorder recorder = new RoutingRecorder();
  private final AsyncOrderService orders = proxied(Mono.just(ORDER), Flux.just("ORD-1"));

  @AfterEach
  void clearThreadState() {
    ThreadBoundContextProvider.clear();
  }

  @Test
  void should_take_the_actor_from_the_subscribers_security_context_when_the_provider_has_none() {
    orders.readOrder("ORD-1").contextWrite(as(ALICE)).block();

    assertThat(recorder.detached().getFirst().actorId()).isEqualTo("alice");
  }

  @Test
  void should_take_the_actor_from_the_subscribers_security_context_for_a_flux() {
    orders.listOrders("CUST-1").contextWrite(as(ALICE)).blockLast();

    assertThat(recorder.detached().getFirst().actorId()).isEqualTo("alice");
  }

  @Test
  void should_take_the_actor_of_each_subscription_from_its_own_context() {
    Mono<Map<String, Object>> read = orders.readOrder("ORD-1");

    read.contextWrite(as(ALICE)).block();
    read.contextWrite(as(BOB)).block();

    assertThat(recorder.detached()).extracting(AuditEvent::actorId).containsExactly("alice", "bob");
  }

  @Test
  void should_prefer_the_providers_actor_over_the_subscribers_security_context() {
    ThreadBoundContextProvider.actAs("carol");
    Mono<Map<String, Object>> read = orders.readOrder("ORD-1");
    ThreadBoundContextProvider.clear();

    read.contextWrite(as(ALICE)).block();

    assertThat(recorder.detached().getFirst().actorId()).isEqualTo("carol");
  }

  @Test
  void should_record_no_actor_when_the_subscriber_is_anonymous() {
    orders.readOrder("ORD-1").contextWrite(as(ANONYMOUS)).block();

    assertThat(recorder.detached().getFirst().actorId()).isNull();
  }

  @Test
  void should_record_no_actor_when_the_subscribers_authentication_is_not_authenticated() {
    orders
        .readOrder("ORD-1")
        .contextWrite(as(new TestingAuthenticationToken("dave", "n/a")))
        .block();

    assertThat(recorder.detached().getFirst().actorId()).isNull();
  }

  @Test
  void should_still_record_the_event_when_the_subscribers_security_context_fails() {
    Context broken = Context.of(SecurityContext.class, Mono.error(new IllegalStateException()));

    orders.readOrder("ORD-1").contextWrite(broken).block();

    assertThat(recorder.detached().getFirst().actorId()).isNull();
  }

  @Test
  void should_record_no_actor_when_the_subscriber_has_no_security_context() {
    orders.readOrder("ORD-1").block();

    assertThat(recorder.detached().getFirst().actorId()).isNull();
  }

  @Test
  void should_record_cancelled_when_a_subscriber_with_a_security_context_cancels() {
    AsyncOrderService slow = proxied(Mono.never(), Flux.empty());

    slow.readOrder("ORD-1").contextWrite(as(ALICE)).subscribe().dispose();

    assertThat(recorder.detached().getFirst().status()).isEqualTo("CANCELLED");
  }

  private static Context as(Authentication authentication) {
    return ReactiveSecurityContextHolder.withAuthentication(authentication);
  }

  private static Authentication user(String name) {
    return new TestingAuthenticationToken(name, "n/a", "ROLE_USER");
  }

  private AsyncOrderService proxied(Mono<Map<String, Object>> mono, Flux<String> flux) {
    var factory =
        new AspectJProxyFactory(new AsyncOrderService(new CompletableFuture<>(), mono, flux));
    factory.setProxyTargetClass(true);
    factory.addAspect(
        new AuditAspect(
            recorder, new ThreadBoundContextProvider(), new AuditAspect.Options(FIXED, false, 0)));
    return factory.getProxy();
  }
}
