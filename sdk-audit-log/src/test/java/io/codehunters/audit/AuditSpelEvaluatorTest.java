package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;

class AuditSpelEvaluatorTest {

  private final AuditSpelEvaluator evaluator = new AuditSpelEvaluator();

  static class Sample {
    public String greet(String name, Integer age) {
      return "hi " + name + " " + age;
    }
  }

  private Method greetMethod() throws NoSuchMethodException {
    return Sample.class.getMethod("greet", String.class, Integer.class);
  }

  @Test
  @DisplayName("should_resolve_variable_by_parameter_name_when_compiled_with_parameters")
  void should_resolve_by_parameter_name() throws Exception {
    EvaluationContext ctx = evaluator.buildContext(greetMethod(), new Object[] {"Ada", 36}, "ok");

    assertThat(evaluator.evaluate("#p0", ctx)).isEqualTo("Ada");
    assertThat(evaluator.evaluate("#arg1", ctx)).isEqualTo("36");
    assertThat(evaluator.evaluate("#result", ctx)).isEqualTo("ok");
  }

  @Test
  @DisplayName("should_return_null_when_expression_blank_or_null")
  void should_return_null_when_expression_blank() throws Exception {
    EvaluationContext ctx = evaluator.buildContext(greetMethod(), new Object[] {"x", 1}, null);

    assertThat(evaluator.evaluate(null, ctx)).isNull();
    assertThat(evaluator.evaluate("", ctx)).isNull();
    assertThat(evaluator.evaluate("   ", ctx)).isNull();
  }

  @Test
  @DisplayName("should_swallow_evaluation_exception_and_return_null")
  void should_swallow_evaluation_exception() throws Exception {
    EvaluationContext ctx = evaluator.buildContext(greetMethod(), new Object[] {"x", 1}, null);

    assertThat(evaluator.evaluate("#missingVar.foo", ctx)).isNull();
  }

  @Test
  @DisplayName("should_build_payload_with_key_value_when_valid")
  void should_build_payload() throws Exception {
    EvaluationContext ctx = evaluator.buildContext(greetMethod(), new Object[] {"Ada", 36}, "ok");

    Map<String, Object> payload =
        evaluator.evaluatePayload(new String[] {"name=#p0", "age=#p1"}, ctx);

    assertThat(payload).containsEntry("name", "Ada").containsEntry("age", 36);
  }

  @Test
  @DisplayName("should_skip_entry_when_value_is_null")
  void should_skip_null_payload_entry() throws Exception {
    EvaluationContext ctx = evaluator.buildContext(greetMethod(), new Object[] {null, null}, null);

    Map<String, Object> payload =
        evaluator.evaluatePayload(new String[] {"name=#p0", "age=#p1"}, ctx);

    assertThat(payload).isEmpty();
  }

  @Test
  @DisplayName("should_skip_expression_when_no_equals_sign")
  void should_skip_expression_without_equals() throws Exception {
    EvaluationContext ctx = evaluator.buildContext(greetMethod(), new Object[] {"a", 1}, null);

    Map<String, Object> payload = evaluator.evaluatePayload(new String[] {"#p0"}, ctx);

    assertThat(payload).isEmpty();
  }

  @Test
  @DisplayName("should_swallow_payload_exception_and_continue_with_other_entries")
  void should_swallow_payload_exception() throws Exception {
    EvaluationContext ctx = evaluator.buildContext(greetMethod(), new Object[] {"a", 1}, null);

    Map<String, Object> payload =
        evaluator.evaluatePayload(new String[] {"bad=#missingVar.foo", "ok=#p0"}, ctx);

    assertThat(payload).containsEntry("ok", "a").doesNotContainKey("bad");
  }

  @Test
  void should_resolve_variable_by_declared_parameter_name() throws Exception {
    EvaluationContext ctx = evaluator.buildContext(greetMethod(), new Object[] {"Ada", 36}, "ok");

    assertThat(evaluator.evaluate("#name", ctx)).isEqualTo("Ada");
    assertThat(evaluator.evaluate("#a1", ctx)).isEqualTo("36");
  }

  @Test
  void should_return_null_when_expression_has_invalid_syntax() throws Exception {
    EvaluationContext ctx = evaluator.buildContext(greetMethod(), new Object[] {"x", 1}, null);

    assertThat(evaluator.evaluate("#p0 +", ctx)).isNull();
  }

  @Test
  void should_skip_payload_entry_when_expression_has_invalid_syntax() throws Exception {
    EvaluationContext ctx = evaluator.buildContext(greetMethod(), new Object[] {"a", 1}, null);

    Map<String, Object> payload =
        evaluator.evaluatePayload(new String[] {"bad=#p0 +", "ok=#p0"}, ctx);

    assertThat(payload).containsOnlyKeys("ok");
  }

  @Test
  void should_parse_each_expression_once_when_evaluated_repeatedly() throws Exception {
    EvaluationContext ctx = evaluator.buildContext(greetMethod(), new Object[] {"a", 1}, null);

    evaluator.evaluate("#p0", ctx);
    evaluator.evaluate("#p0", ctx);

    assertThat(evaluator.cachedExpressions()).isEqualTo(1);
  }

  @Test
  void should_stop_caching_but_keep_evaluating_when_cache_is_full() throws Exception {
    var bounded = new AuditSpelEvaluator(1);
    EvaluationContext ctx = bounded.buildContext(greetMethod(), new Object[] {"a", 1}, null);

    bounded.evaluate("#p0", ctx);

    assertThat(bounded.evaluate("#p1", ctx)).isEqualTo("1");
    assertThat(bounded.cachedExpressions()).isEqualTo(1);
  }
}
