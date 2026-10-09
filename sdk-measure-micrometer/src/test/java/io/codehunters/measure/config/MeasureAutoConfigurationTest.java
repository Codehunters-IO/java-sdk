package io.codehunters.measure.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.measure.Measured;
import io.codehunters.measure.internal.MeasureAspect;
import io.codehunters.measure.internal.OrderService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MeasureAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MeasureAutoConfiguration.class,
                  AopAutoConfiguration.class,
                  MetricsAutoConfiguration.class,
                  CompositeMeterRegistryAutoConfiguration.class,
                  SimpleMetricsExportAutoConfiguration.class));

  @Test
  void should_measure_a_bean_with_boots_metrics_and_aop_auto_configuration() {
    runner
        .withBean(OrderService.class)
        .run(
            ctx -> {
              ctx.getBean(OrderService.class).place("ORD-1");
              assertThat(ctx.getBean(MeterRegistry.class).get("orders.place").timer().count())
                  .isEqualTo(1);
            });
  }

  @Test
  void should_apply_the_configured_cap_on_expression_tag_values() {
    runner
        .withBean(OrderService.class)
        .withPropertyValues("codehunters.measure.max-tag-values=1")
        .run(
            ctx -> {
              ctx.getBean(OrderService.class).byCustomer("CUST-1");
              ctx.getBean(OrderService.class).byCustomer("CUST-2");
              assertThat(
                      ctx.getBean(MeterRegistry.class)
                          .get("orders.by.customer")
                          .tag("customer", "other")
                          .timer()
                          .count())
                  .isEqualTo(1);
            });
  }

  @Test
  void should_not_register_the_aspect_when_disabled() {
    runner
        .withPropertyValues("codehunters.measure.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(MeasureAspect.class));
  }

  @Test
  void should_not_register_the_aspect_without_a_meter_registry() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MeasureAutoConfiguration.class))
        .run(ctx -> assertThat(ctx).doesNotHaveBean(MeasureAspect.class));
  }

  @Test
  void should_not_enable_auto_proxying_on_its_own() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MeasureAutoConfiguration.class))
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(ctx -> assertThat(ctx).doesNotHaveBean(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME));
  }

  @Test
  void should_back_off_when_the_application_defines_its_own_aspect() {
    runner
        .withBean(
            "customAspect", MeasureAspect.class, () -> new MeasureAspect(new SimpleMeterRegistry()))
        .run(
            ctx ->
                assertThat(ctx).getBean(MeasureAspect.class).isSameAs(ctx.getBean("customAspect")));
  }

  @Test
  void should_fail_startup_when_max_tag_values_is_below_one() {
    runner
        .withPropertyValues("codehunters.measure.max-tag-values=0")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void should_fail_startup_when_a_bean_has_an_odd_number_of_tags() {
    runner
        .withBean(OddTags.class)
        .run(ctx -> assertThat(ctx).getFailure().hasStackTraceContaining("key/value pairs"));
  }

  @Test
  void should_fail_startup_when_a_class_annotation_uses_a_reserved_tag_key() {
    runner
        .withBean(ReservedKey.class)
        .run(ctx -> assertThat(ctx).getFailure().hasStackTraceContaining("reserved"));
  }

  @Test
  void should_fail_startup_when_an_expression_is_not_key_equals_expression() {
    runner
        .withBean(MalformedExpression.class)
        .run(ctx -> assertThat(ctx).getFailure().hasStackTraceContaining("key=expression"));
  }

  @Test
  void should_validate_measured_beans_even_when_measuring_is_disabled_by_missing_registry() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MeasureAutoConfiguration.class))
        .withBean(OddTags.class)
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  static class OddTags {
    @Measured(
        name = "odd",
        tags = {"region"})
    void call() {}
  }

  @Measured(
      name = "reserved",
      tags = {"outcome", "x"})
  static class ReservedKey {
    void call() {}
  }

  static class MalformedExpression {
    @Measured(
        name = "malformed",
        expressions = {"#customerId"})
    void call(String customerId) {}
  }
}
