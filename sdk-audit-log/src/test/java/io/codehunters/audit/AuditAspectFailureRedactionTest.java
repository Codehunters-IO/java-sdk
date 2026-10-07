package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Publisher failures never carry the event's data into exceptions or logs. */
@ExtendWith(OutputCaptureExtension.class)
class AuditAspectFailureRedactionTest {

  private static final String ROW_VALUE = "alice@example.com";
  private static final Clock FIXED = Clock.fixed(TestConfig.NOW, ZoneOffset.UTC);

  private final AuditEventPublisherPort leaking =
      event -> {
        throw new IllegalStateException("Failing row contains (" + ROW_VALUE + ")");
      };

  @Test
  void should_fail_the_call_with_a_redacted_exception_when_fail_on_error_is_set() {
    OrderService orders = proxied(leaking, FIXED, true);

    assertThatThrownBy(() -> orders.findOrder("ORD-1"))
        .isInstanceOf(AuditPersistenceException.class)
        .satisfies(failure -> assertThat(stackTraceOf(failure)).doesNotContain(ROW_VALUE));
  }

  @Test
  void should_suppress_a_redacted_exception_when_the_method_fails_and_fail_on_error_is_set() {
    OrderService orders = proxied(leaking, FIXED, true);

    assertThatThrownBy(() -> orders.deleteOrder("ORD-2"))
        .isInstanceOf(IllegalStateException.class)
        .satisfies(failure -> assertThat(stackTraceOf(failure)).doesNotContain(ROW_VALUE));
  }

  @Test
  void should_log_only_the_exception_class_when_publishing_fails(CapturedOutput output) {
    OrderService orders = proxied(leaking, FIXED, false);

    orders.findOrder("ORD-3");

    assertThat(output).contains(IllegalStateException.class.getName());
    assertThat(output).doesNotContain(ROW_VALUE);
  }

  @Test
  void should_log_only_the_exception_class_when_the_event_cannot_be_built(CapturedOutput output) {
    OrderService orders = proxied(event -> {}, new BrokenClock(), false);

    orders.findOrder("ORD-4");

    assertThat(output).contains(IllegalStateException.class.getName());
    assertThat(output).doesNotContain("clock unavailable");
  }

  private static OrderService proxied(
      AuditEventPublisherPort publisher, Clock clock, boolean failOnError) {
    var factory = new AspectJProxyFactory(new OrderService());
    factory.setProxyTargetClass(true);
    factory.addAspect(
        new AuditAspect(
            publisher,
            new NoOpAuditContextProvider(),
            new AuditAspect.Options(clock, failOnError, 0)));
    return factory.getProxy();
  }

  private static String stackTraceOf(Throwable throwable) {
    var out = new StringWriter();
    throwable.printStackTrace(new PrintWriter(out));
    return out.toString();
  }
}
