package io.codehunters.audit.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.audit.AuditEventPublisherPort;
import io.codehunters.audit.AuditPersistenceException;
import io.codehunters.audit.NoOpAuditContextProvider;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class AuditAspectPublishFailureTest {

  private final AuditPersistenceException persistenceFailure =
      new AuditPersistenceException("Failed to persist audit event", new IllegalStateException());

  @Test
  void should_propagate_persistence_failure_when_method_succeeds() {
    OrderService orders = proxied(event -> throwPersistenceFailure());

    assertThatThrownBy(() -> orders.findOrder("ORD-1")).isSameAs(persistenceFailure);
  }

  @Test
  void should_keep_method_exception_and_suppress_persistence_failure_when_method_throws() {
    OrderService orders = proxied(event -> throwPersistenceFailure());

    assertThatThrownBy(() -> orders.deleteOrder("ORD-2"))
        .isInstanceOf(IllegalStateException.class)
        .hasSuppressedException(persistenceFailure);
  }

  @Test
  void should_return_result_when_publisher_fails_with_other_exception() {
    OrderService orders =
        proxied(
            event -> {
              throw new IllegalStateException("sink down");
            });

    assertThat(orders.findOrder("ORD-3")).isEqualTo(Map.of("id", "ORD-3", "status", "ACTIVE"));
  }

  private void throwPersistenceFailure() {
    throw persistenceFailure;
  }

  private static OrderService proxied(AuditEventPublisherPort publisher) {
    var factory = new AspectJProxyFactory(new OrderService());
    factory.setProxyTargetClass(true);
    factory.addAspect(
        new AuditAspect(publisher, new NoOpAuditContextProvider(), Clock.systemUTC()));
    return factory.getProxy();
  }
}
