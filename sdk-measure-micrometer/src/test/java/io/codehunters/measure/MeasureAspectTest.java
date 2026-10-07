package io.codehunters.measure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class MeasureAspectTest {

  private static final String ORDERS = OrderService.class.getName();
  private static final int MAX_TAG_VALUES = 2;
  private static final Tags PLACE_SUCCESS_TAGS =
      Tags.of(
          "class", ORDERS,
          "method", "place",
          "exception", "none",
          "outcome", "SUCCESS",
          "channel", "web");

  private final MeterRegistry registry = new SimpleMeterRegistry();
  private final OrderService orders = proxied(new OrderService());

  @Test
  void should_record_a_timer_with_class_method_exception_and_outcome_tags_when_method_succeeds() {
    orders.place("ORD-1");

    assertThat(registry.get("orders.place").tags(PLACE_SUCCESS_TAGS).timer().count()).isEqualTo(1);
  }

  @Test
  void should_return_the_methods_result() {
    assertThat(orders.place("ORD-2")).isEqualTo("placed-ORD-2");
  }

  @Test
  void should_name_the_timer_method_measured_when_no_name_is_given() {
    orders.byDefaultName();

    assertThat(registry.get("method.measured").tag("method", "byDefaultName").timer().count())
        .isEqualTo(1);
  }

  @Test
  void should_tag_the_exception_simple_name_and_error_outcome_when_method_throws() {
    assertThatThrownBy(orders::fail).isInstanceOf(IllegalStateException.class);

    assertThat(
            registry
                .get("orders.fail")
                .tags("exception", "IllegalStateException", "outcome", "ERROR")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void should_not_register_a_separate_error_counter_when_method_throws() {
    assertThatThrownBy(orders::fail).isInstanceOf(IllegalStateException.class);

    assertThat(registry.find("orders.fail.errors").meters()).isEmpty();
  }

  @Test
  void should_tag_exception_none_when_record_exceptions_is_off() {
    assertThatThrownBy(orders::failQuietly).isInstanceOf(IllegalStateException.class);

    assertThat(
            registry
                .get("orders.fail.quiet")
                .tags("exception", "none", "outcome", "ERROR")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void should_tag_the_value_of_an_expression_on_a_parameter_name() {
    orders.byCustomer("CUST-1");

    assertThat(registry.get("orders.by.customer").tag("customer", "CUST-1").timer().count())
        .isEqualTo(1);
  }

  @Test
  void should_tag_the_value_of_an_expression_on_a_parameter_position() {
    orders.byPosition("CUST-2");

    assertThat(registry.get("orders.by.position").tag("customer", "CUST-2").timer().count())
        .isEqualTo(1);
  }

  @Test
  void should_tag_none_when_the_expression_value_is_null() {
    orders.byCustomer(null);

    assertThat(registry.get("orders.by.customer").tag("customer", "none").timer().count())
        .isEqualTo(1);
  }

  @Test
  void should_tag_unknown_when_the_expression_fails_to_evaluate() {
    orders.missingMethod("CUST-3");

    assertThat(registry.get("orders.missing.method").tag("customer", "unknown").timer().count())
        .isEqualTo(1);
  }

  @Test
  void should_tag_unknown_when_the_expression_cannot_be_parsed() {
    orders.unparseable("CUST-4");

    assertThat(registry.get("orders.unparseable").tag("customer", "unknown").timer().count())
        .isEqualTo(1);
  }

  @Test
  void should_tag_unknown_when_the_expression_references_a_type() {
    orders.typeReference();

    assertThat(registry.get("orders.type.reference").tag("cpus", "unknown").timer().count())
        .isEqualTo(1);
  }

  @Test
  void should_allow_instance_methods_in_expressions() {
    orders.instanceMethod("cust-5");

    assertThat(registry.get("orders.instance.method").tag("customer", "CUST-5").timer().count())
        .isEqualTo(1);
  }

  @Test
  void should_tag_none_when_a_static_tag_value_is_blank() {
    orders.blankStaticTag();

    assertThat(registry.get("orders.blank.static").tag("region", "none").timer().count())
        .isEqualTo(1);
  }

  @Test
  void should_replace_values_beyond_the_cardinality_limit_with_other() {
    orders.byCustomer("CUST-A");
    orders.byCustomer("CUST-B");

    orders.byCustomer("CUST-C");

    assertThat(registry.get("orders.by.customer").tag("customer", "other").timer().count())
        .isEqualTo(1);
    assertThat(registry.find("orders.by.customer").tag("customer", "CUST-C").meters()).isEmpty();
  }

  @Test
  void should_keep_recording_known_values_once_the_cardinality_limit_is_reached() {
    orders.byCustomer("CUST-A");
    orders.byCustomer("CUST-B");
    orders.byCustomer("CUST-C");

    orders.byCustomer("CUST-A");

    assertThat(registry.get("orders.by.customer").tag("customer", "CUST-A").timer().count())
        .isEqualTo(2);
  }

  @Test
  void should_increment_the_counter_by_the_count_value() {
    orders.counted();

    assertThat(
            registry
                .get("orders.counted.counted")
                .tags("class", ORDERS, "method", "counted")
                .counter()
                .count())
        .isEqualTo(2.5);
  }

  @Test
  void should_count_when_the_condition_holds() {
    orders.conditional("OK");

    assertThat(registry.get("orders.conditional.counted").counter().count()).isEqualTo(1);
  }

  @Test
  void should_not_count_when_the_condition_does_not_hold() {
    orders.conditional("FAIL");

    assertThat(registry.find("orders.conditional.counted").counter()).isNull();
  }

  @Test
  void should_not_count_and_still_return_when_the_condition_fails_to_evaluate() {
    assertThat(orders.badCondition()).isEqualTo("ok");

    assertThat(registry.find("orders.bad.condition.counted").counter()).isNull();
  }

  @Test
  void should_track_the_call_as_an_active_long_task_while_it_runs() {
    var activeDuringCall = new AtomicInteger();

    orders.longOperation(
        () ->
            activeDuringCall.set(registry.get("orders.long.active").longTaskTimer().activeTasks()));

    assertThat(activeDuringCall).hasValue(1);
    assertThat(registry.get("orders.long.active").longTaskTimer().activeTasks()).isZero();
  }

  @Test
  void should_not_record_the_timer_before_the_future_completes() {
    orders.async(new CompletableFuture<>());

    assertThat(registry.find("orders.async").timer()).isNull();
  }

  @Test
  void should_record_success_when_the_future_completes() {
    var pending = new CompletableFuture<String>();
    orders.async(pending);

    pending.complete("done");

    assertThat(registry.get("orders.async").tag("outcome", "SUCCESS").timer().count()).isEqualTo(1);
    assertThat(registry.get("orders.async.counted").counter().count()).isEqualTo(1);
  }

  @Test
  void should_record_the_cause_when_the_future_fails_with_a_completion_exception() {
    var pending = new CompletableFuture<String>();
    orders.async(pending);

    pending.completeExceptionally(new CompletionException(new IllegalArgumentException()));

    assertThat(
            registry
                .get("orders.async")
                .tags("outcome", "ERROR", "exception", "IllegalArgumentException")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void should_return_the_methods_own_future() {
    var pending = new CompletableFuture<String>();

    assertThat(orders.async(pending)).isSameAs(pending);
  }

  @Test
  void should_measure_every_method_of_a_class_annotated_with_measured() {
    InventoryService inventory = proxied(new InventoryService());

    inventory.reserve("SKU-1");

    assertThat(registry.get("inventory").tag("method", "reserve").timer().count()).isEqualTo(1);
  }

  @Test
  void should_prefer_the_method_annotation_over_the_class_annotation() {
    InventoryService inventory = proxied(new InventoryService());

    inventory.release("SKU-2");

    assertThat(registry.get("inventory.release").timer().count()).isEqualTo(1);
    assertThat(registry.find("inventory").tag("method", "release").timer()).isNull();
  }

  @Test
  void should_not_fail_the_call_when_the_registry_rejects_the_meter() {
    registry.counter("orders.place", PLACE_SUCCESS_TAGS);

    assertThat(orders.place("ORD-3")).isEqualTo("placed-ORD-3");
  }

  @Test
  void should_call_the_method_without_meters_when_its_annotation_is_invalid() {
    OddTagsService odd = proxied(new OddTagsService());

    assertThat(odd.call()).isEqualTo("called");
    assertThat(registry.find("odd").meters()).isEmpty();
  }

  @Test
  void should_keep_recording_other_after_the_cap_was_reported() {
    orders.byCustomer("CUST-A");
    orders.byCustomer("CUST-B");
    orders.byCustomer("CUST-C");

    orders.byCustomer("CUST-D");

    assertThat(registry.get("orders.by.customer").tag("customer", "other").timer().count())
        .isEqualTo(2);
  }

  /** Not validated at startup because it is proxied by hand. */
  static class OddTagsService {
    @Measured(
        name = "odd",
        tags = {"region"})
    String call() {
      return "called";
    }
  }

  private <T> T proxied(T target) {
    var factory = new AspectJProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAspect(new MeasureAspect(registry, MAX_TAG_VALUES));
    return factory.getProxy();
  }
}
