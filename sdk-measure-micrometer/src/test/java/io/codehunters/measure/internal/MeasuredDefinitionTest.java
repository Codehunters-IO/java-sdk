package io.codehunters.measure.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.codehunters.measure.Measured;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.annotation.AnnotationUtils;

class MeasuredDefinitionTest {

  @Test
  void should_name_the_timer_method_measured_when_the_name_is_blank() {
    assertThat(MeasuredDefinition.of(measured(Map.of())).name()).isEqualTo("method.measured");
  }

  @Test
  void should_keep_the_static_tags_in_pairs() {
    var definition =
        MeasuredDefinition.of(measured(Map.of("tags", new String[] {"channel", "web"})));

    assertThat(definition.staticTags()).isEqualTo(Tags.of("channel", "web"));
  }

  @Test
  void should_reject_a_blank_tag_key() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> MeasuredDefinition.of(measured(Map.of("tags", new String[] {" ", "x"}))))
        .withMessageContaining("blank");
  }

  @Test
  void should_reject_a_key_used_by_both_a_tag_and_an_expression() {
    Map<String, Object> attributes =
        Map.of(
            "tags", new String[] {"customer", "x"},
            "expressions", new String[] {"customer=#p0"});

    assertThatIllegalArgumentException()
        .isThrownBy(() -> MeasuredDefinition.of(measured(attributes)))
        .withMessageContaining("repeated");
  }

  @Test
  void should_reject_an_expression_without_a_value() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                MeasuredDefinition.of(measured(Map.of("expressions", new String[] {"customer="}))))
        .withMessageContaining("key=expression");
  }

  @Test
  void should_have_no_count_condition_when_it_is_blank() {
    assertThat(MeasuredDefinition.of(measured(Map.of())).countCondition()).isNull();
  }

  @Test
  void should_reject_an_expression_whose_key_is_not_a_tag_name() {
    Measured measured = measured(Map.of("expressions", new String[] {"#status == 'OK'"}));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> MeasuredDefinition.of(measured))
        .withMessageContaining("#status");
  }

  @Test
  void should_reject_a_static_tag_key_that_is_not_a_tag_name() {
    Measured measured = measured(Map.of("tags", new String[] {"region name", "eu"}));

    assertThatIllegalArgumentException().isThrownBy(() -> MeasuredDefinition.of(measured));
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
  void should_reject_a_count_value_that_is_not_positive_and_finite(double countValue) {
    Measured measured = measured(Map.of("count", true, "countValue", countValue));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> MeasuredDefinition.of(measured))
        .withMessageContaining("countValue");
  }

  private static Measured measured(Map<String, Object> attributes) {
    return AnnotationUtils.synthesizeAnnotation(attributes, Measured.class, null);
  }
}
