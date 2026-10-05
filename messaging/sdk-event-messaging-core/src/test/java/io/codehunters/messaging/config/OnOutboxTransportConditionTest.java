package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

class OnOutboxTransportConditionTest {

  private static final String TYPE_KEY = "codehunters.outbox.publisher.type";
  private static final String PRESENT = "java.lang.String";
  private static final String ABSENT = "io.codehunters.absent.Marker";

  private final MockEnvironment environment = new MockEnvironment();
  private final ConditionContext context = mock(ConditionContext.class);
  private final AnnotatedTypeMetadata rabbitMetadata = mock(AnnotatedTypeMetadata.class);

  @BeforeEach
  void setUp() {
    when(context.getEnvironment()).thenReturn(environment);
    when(context.getClassLoader()).thenReturn(getClass().getClassLoader());
    when(rabbitMetadata.getAnnotationAttributes(ConditionalOnOutboxTransport.class.getName()))
        .thenReturn(Map.of("value", "rabbit"));
  }

  @Test
  void should_match_when_explicit_type_names_the_transport_ignoring_case() {
    environment.setProperty(TYPE_KEY, " Rabbit ");

    assertThat(matches(condition(PRESENT, PRESENT))).isTrue();
  }

  @Test
  void should_not_match_when_explicit_type_names_another_transport() {
    environment.setProperty(TYPE_KEY, "kafka");

    assertThat(matches(condition(PRESENT, ABSENT))).isFalse();
  }

  @Test
  void should_match_when_type_unset_and_only_this_transport_artifact_present() {
    assertThat(matches(condition(PRESENT, ABSENT))).isTrue();
  }

  @Test
  void should_not_match_when_type_blank_and_only_other_transport_artifact_present() {
    environment.setProperty(TYPE_KEY, " ");

    assertThat(matches(condition(ABSENT, PRESENT))).isFalse();
  }

  @Test
  void should_fail_naming_type_key_when_type_unset_and_both_transport_artifacts_present() {
    OnOutboxTransportCondition condition = condition(PRESENT, PRESENT);

    assertThatIllegalStateException()
        .isThrownBy(() -> condition.getMatchOutcome(context, rabbitMetadata))
        .withMessageContaining(TYPE_KEY);
  }

  @Test
  void should_resolve_bundled_transport_artifacts_when_created_by_spring() {
    environment.setProperty(TYPE_KEY, "rabbit");

    assertThat(matches(new OnOutboxTransportCondition())).isTrue();
  }

  private boolean matches(OnOutboxTransportCondition condition) {
    return condition.getMatchOutcome(context, rabbitMetadata).isMatch();
  }

  private static OnOutboxTransportCondition condition(String rabbitMarker, String kafkaMarker) {
    return new OnOutboxTransportCondition(Map.of("rabbit", rabbitMarker, "kafka", kafkaMarker));
  }
}
