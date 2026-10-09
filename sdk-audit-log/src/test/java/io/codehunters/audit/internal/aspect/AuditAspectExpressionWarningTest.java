package io.codehunters.audit.internal.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.audit.Auditable;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * An expression that fails at run time leaves its field {@code null}, as before, but is no longer
 * silent: the first failure per method and attribute is a warning that names neither the
 * expression's values nor the exception message.
 */
@ExtendWith(OutputCaptureExtension.class)
class AuditAspectExpressionWarningTest {

  private static final Clock FIXED = Clock.fixed(TestConfig.NOW, ZoneOffset.UTC);
  private static final String SECRET = "4111-1111-1111-1111";
  private static final String WARNING = "could not be evaluated";

  private final InMemoryAuditEventPublisher publisher = new InMemoryAuditEventPublisher();
  private final CardService cards = proxied();

  @Test
  void should_record_null_for_an_expression_that_fails_at_run_time() {
    cards.charge(SECRET);

    assertThat(publisher.getLastEvent().entityId()).isNull();
  }

  @Test
  void should_warn_once_per_method_and_attribute(CapturedOutput output) {
    cards.charge(SECRET);
    cards.charge(SECRET);

    assertThat(output.getOut())
        .containsOnlyOnce(WARNING + " for entityId of " + CardService.class.getName() + "#charge");
  }

  @Test
  void should_warn_separately_for_each_attribute(CapturedOutput output) {
    cards.charge(SECRET);

    assertThat(output.getOut())
        .containsOnlyOnce("for payload[last4] of " + CardService.class.getName() + "#charge");
  }

  @Test
  void should_not_log_the_values_of_the_failing_expression(CapturedOutput output) {
    cards.charge(SECRET);

    assertThat(output).doesNotContain(SECRET);
  }

  @Test
  void should_not_warn_when_result_expression_fails_because_the_method_threw(
      CapturedOutput output) {
    assertThatThrownBy(() -> cards.decline("C-1")).isInstanceOf(IllegalStateException.class);

    assertThat(output.getOut()).doesNotContain(WARNING);
  }

  @Test
  void should_warn_separately_for_each_overload(CapturedOutput output) {
    cards.refund(SECRET);
    cards.refund(SECRET, 1);

    assertThat(output.getOut()).contains("#refund(String)", "#refund(String, int)");
  }

  private CardService proxied() {
    var factory = new AspectJProxyFactory(new CardService());
    factory.setProxyTargetClass(true);
    factory.addAspect(
        new AuditAspect(
            publisher, new ThreadBoundContextProvider(), new AuditAspect.Options(FIXED, false, 0)));
    return factory.getProxy();
  }

  /** Audits with expressions that parse but fail against the arguments. */
  public static class CardService {

    @Auditable(
        entityType = "CARD",
        action = "CHARGE",
        entityId = "#card.noSuchProperty",
        payload = {"last4=#card.substring(99)"})
    public void charge(String card) {
      // audited only
    }

    @Auditable(entityType = "CARD", action = "DECLINE", entityId = "#result.id")
    public Object decline(String card) {
      throw new IllegalStateException("declined");
    }

    @Auditable(entityType = "CARD", action = "REFUND", entityId = "#card.noSuchProperty")
    public void refund(String card) {
      // audited only
    }

    @Auditable(entityType = "CARD", action = "REFUND", entityId = "#card.noSuchProperty")
    public void refund(String card, int amount) {
      // audited only
    }
  }
}
