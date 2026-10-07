package io.codehunters.measure;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** A misconfigured meter is reported once, not on every call. */
@ExtendWith(OutputCaptureExtension.class)
class MeasureWarningsTest {

  private final MeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void should_warn_once_when_the_registry_keeps_rejecting_a_meter(CapturedOutput output) {
    registry.counter(
        "orders.place",
        Tags.of(
            "class", OrderService.class.getName(),
            "method", "place",
            "exception", "none",
            "outcome", "SUCCESS",
            "channel", "web"));
    OrderService orders = proxied(new OrderService());

    orders.place("ORD-1");
    orders.place("ORD-2");

    assertThat(output.getOut()).containsOnlyOnce("Could not record @Measured meters");
  }

  @Test
  void should_warn_once_when_an_annotation_is_invalid(CapturedOutput output) {
    MeasureAspectTest.OddTagsService odd = proxied(new MeasureAspectTest.OddTagsService());

    odd.call();
    odd.call();

    assertThat(output.getOut()).containsOnlyOnce("Could not start @Measured meters");
  }

  @Test
  void should_warn_once_per_method_when_methods_share_a_rejected_name(CapturedOutput output) {
    registry.counter(
        "orders.region",
        Tags.of(
            "class", OrderService.class.getName(),
            "method", "inNorth",
            "exception", "none",
            "outcome", "SUCCESS",
            "region", "R1"));
    registry.counter(
        "orders.region",
        Tags.of(
            "class", OrderService.class.getName(),
            "method", "inSouth",
            "exception", "none",
            "outcome", "SUCCESS",
            "region", "R1"));
    OrderService orders = proxied(new OrderService());

    orders.inNorth("R1");
    orders.inSouth("R1");

    assertThat(output.getOut())
        .containsPattern("WARN.*OrderService\\.inNorth")
        .containsPattern("WARN.*OrderService\\.inSouth");
  }

  private <T> T proxied(T target) {
    var factory = new AspectJProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAspect(new MeasureAspect(registry));
    return factory.getProxy();
  }
}
