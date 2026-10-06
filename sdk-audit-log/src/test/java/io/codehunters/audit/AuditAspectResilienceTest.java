package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class AuditAspectResilienceTest {

  private static final Clock FIXED = Clock.fixed(TestConfig.NOW, ZoneOffset.UTC);

  private final InMemoryAuditEventPublisher published = new InMemoryAuditEventPublisher();

  @Test
  void should_publish_event_without_actor_when_context_provider_throws() {
    OrderService orders = proxied(published, new FailingActorProvider(), options(FIXED, false));

    orders.findOrder("ORD-1");

    assertThat(published.getLastEvent().actorId()).isNull();
    assertThat(published.getLastEvent().entityId()).isEqualTo("ORD-1");
  }

  @Test
  void should_fail_call_when_event_cannot_be_built_and_fail_on_error_is_set() {
    OrderService orders =
        proxied(published, new NoOpAuditContextProvider(), options(new BrokenClock(), true));

    assertThatThrownBy(() -> orders.findOrder("ORD-2"))
        .isInstanceOf(AuditPersistenceException.class)
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_return_result_when_event_cannot_be_built_and_fail_on_error_is_not_set() {
    OrderService orders =
        proxied(published, new NoOpAuditContextProvider(), options(new BrokenClock(), false));

    assertThat(orders.findOrder("ORD-3")).isEqualTo(Map.of("id", "ORD-3", "status", "ACTIVE"));
  }

  @Test
  void should_keep_method_exception_when_publisher_throws_an_error() {
    AuditEventPublisherPort failing =
        event -> {
          throw new LinkageError("serializer missing");
        };
    OrderService orders = proxied(failing, new NoOpAuditContextProvider(), options(FIXED, false));

    assertThatThrownBy(() -> orders.deleteOrder("ORD-4"))
        .isInstanceOf(IllegalStateException.class)
        .satisfies(ex -> assertThat(ex.getSuppressed()).hasOnlyElementsOfType(LinkageError.class));
  }

  @Test
  void should_expose_the_configured_order() {
    var aspect = new AuditAspect(published, new NoOpAuditContextProvider(), options(FIXED, false));

    assertThat(aspect.getOrder()).isEqualTo(7);
  }

  private static AuditAspect.Options options(Clock clock, boolean failOnError) {
    return new AuditAspect.Options(clock, failOnError, 7);
  }

  private static OrderService proxied(
      AuditEventPublisherPort publisher,
      AuditContextProvider provider,
      AuditAspect.Options options) {
    var factory = new AspectJProxyFactory(new OrderService());
    factory.setProxyTargetClass(true);
    factory.addAspect(new AuditAspect(publisher, provider, options));
    return factory.getProxy();
  }

  private static final class FailingActorProvider implements AuditContextProvider {
    @Override
    public Optional<String> getActorId() {
      throw new IllegalStateException("no security context");
    }

    @Override
    public Optional<String> getActorType() {
      return Optional.empty();
    }

    @Override
    public Optional<String> getIpAddress() {
      return Optional.empty();
    }

    @Override
    public Optional<String> getSourceService() {
      return Optional.empty();
    }
  }

  private static final class BrokenClock extends Clock {
    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      throw new IllegalStateException("clock unavailable");
    }
  }
}
