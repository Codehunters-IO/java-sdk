package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class AuditAspectInterfaceProxyTest {

  private final InMemoryAuditEventPublisher published = new InMemoryAuditEventPublisher();

  interface Payments {
    String pay(String paymentId, long amount);
  }

  static class AuditedPayments implements Payments {
    @Override
    @Auditable(
        entityType = "PAYMENT",
        action = "PAY",
        entityId = "#paymentId",
        payload = {"amount=#amount"})
    public String pay(String paymentId, long amount) {
      return "receipt-" + paymentId;
    }
  }

  @Test
  void should_resolve_parameters_by_name_through_a_jdk_interface_proxy() {
    Payments payments = interfaceProxy();

    payments.pay("PAY-1", 250L);

    assertThat(published.getLastEvent().entityId()).isEqualTo("PAY-1");
    assertThat(published.getLastEvent().payload()).containsEntry("amount", 250L);
  }

  private Payments interfaceProxy() {
    var factory = new AspectJProxyFactory(new AuditedPayments());
    factory.addInterface(Payments.class);
    factory.addAspect(
        new AuditAspect(
            published,
            new NoOpAuditContextProvider(),
            Clock.fixed(TestConfig.NOW, ZoneOffset.UTC)));
    return factory.getProxy();
  }
}
