package io.codehunters.audit.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.audit.Auditable;
import io.codehunters.audit.internal.aspect.InMemoryAuditEventPublisher;
import io.codehunters.audit.internal.aspect.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** An {@code @Auditable} expression that cannot be parsed is reported at startup. */
@ExtendWith(OutputCaptureExtension.class)
class AuditExpressionStartupCheckTest {

  private static final String INVALID = "Invalid @Auditable expression";
  private static final String SANDBOX = "the sandbox only reads properties";
  private static final String FAIL_FAST = "codehunters.audit.startup-check.fail-fast=true";
  private static final String FAIL_ON_ERROR = "codehunters.audit.fail-on-error=true";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(AopAutoConfiguration.class, AuditAutoConfiguration.class))
          .withBean(InMemoryAuditEventPublisher.class, InMemoryAuditEventPublisher::new);

  private final ApplicationContextRunner broken =
      runner.withBean(BrokenExpressions.class, BrokenExpressions::new);

  @Test
  void should_fail_startup_naming_the_method_and_attribute_when_fail_fast_is_set() {
    broken
        .withPropertyValues(FAIL_FAST)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .hasMessageContaining(INVALID)
                    .hasMessageContaining(BrokenExpressions.class.getName() + "#find")
                    .hasMessageContaining("entityId"));
  }

  @Test
  void should_name_a_payload_entry_by_its_key() {
    broken
        .withPropertyValues(FAIL_FAST)
        .run(context -> assertThat(context).getFailure().hasMessageContaining("payload[amount]"));
  }

  @Test
  void should_log_an_error_and_start_when_fail_fast_is_not_set(CapturedOutput output) {
    broken.run(context -> assertThat(context).hasNotFailed());

    assertThat(output).contains("ERROR", INVALID, BrokenExpressions.class.getName() + "#find");
  }

  @Test
  void should_only_log_when_fail_on_error_is_set_without_fail_fast(CapturedOutput output) {
    broken.withPropertyValues(FAIL_ON_ERROR).run(context -> assertThat(context).hasNotFailed());

    assertThat(output).contains("ERROR", INVALID);
  }

  @Test
  void should_accept_valid_expressions(CapturedOutput output) {
    runner
        .withBean(OrderService.class, OrderService::new)
        .withPropertyValues(FAIL_FAST)
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).doesNotContain(INVALID);
  }

  @Test
  void should_reject_expressions_the_sandbox_cannot_evaluate() {
    runner
        .withBean(SandboxedExpressions.class, SandboxedExpressions::new)
        .withPropertyValues(FAIL_FAST)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .hasMessageContaining(SANDBOX)
                    .hasMessageContaining("entityId: calls a method")
                    .hasMessageContaining("actorId: references a type")
                    .hasMessageContaining("correlationId: calls a constructor")
                    .hasMessageContaining("payload[bean]: references a bean")
                    .hasMessageContaining("payload[assigned]: assigns a value")
                    .hasMessageContaining("#order.id"));
  }

  @Test
  void should_accept_property_paths_indexes_and_safe_navigation(CapturedOutput output) {
    runner
        .withBean(PropertyExpressions.class, PropertyExpressions::new)
        .withPropertyValues(FAIL_FAST)
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).doesNotContain(INVALID);
  }

  /** Declares expressions that parse but use what the sandbox refuses. */
  static class SandboxedExpressions {

    @Auditable(
        entityType = "ORDER",
        action = "READ",
        entityId = "#orderId.toUpperCase()",
        actorId = "T(java.lang.System).getProperty('user.name')",
        correlationId = "new java.lang.String('x')",
        payload = {"bean=@dataSource", "assigned=#orderId = 'x'"})
    public void find(String orderId) {
      // audited only
    }
  }

  /** Declares expressions the sandbox evaluates. */
  static class PropertyExpressions {

    @Auditable(
        entityType = "ORDER",
        action = "READ",
        entityId = "#tags['id']",
        actorId = "#result?.name",
        payload = {"first=#tags['first']", "upper=#tags?.['k'] ?: 'none'"})
    public Object find(java.util.Map<String, String> tags) {
      return null;
    }
  }

  /** Declares expressions SpEL cannot parse. */
  static class BrokenExpressions {

    @Auditable(
        entityType = "ORDER",
        action = "READ",
        entityId = "#orderId )(",
        payload = {"amount=#orderId.(", "plain=#orderId"})
    public void find(String orderId) {
      // audited only
    }
  }
}
