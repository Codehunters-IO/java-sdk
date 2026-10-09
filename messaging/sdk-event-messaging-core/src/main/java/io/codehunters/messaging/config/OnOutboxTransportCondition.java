package io.codehunters.messaging.config;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/** Resolves the outbox transport for {@link ConditionalOnOutboxTransport}. */
class OnOutboxTransportCondition extends SpringBootCondition {

  private static final String TYPE_KEY = "codehunters.outbox.publisher.type";
  private static final Map<String, String> ARTIFACT_MARKERS =
      Map.of(
          "rabbit", "io.codehunters.messaging.rabbit.config.EventMessagingRabbitAutoConfiguration",
          "kafka", "io.codehunters.messaging.kafka.config.EventMessagingKafkaAutoConfiguration");

  private final Map<String, String> artifactMarkers;

  OnOutboxTransportCondition() {
    this(ARTIFACT_MARKERS);
  }

  OnOutboxTransportCondition(Map<String, String> artifactMarkers) {
    this.artifactMarkers = artifactMarkers;
  }

  @Override
  public ConditionOutcome getMatchOutcome(
      ConditionContext context, AnnotatedTypeMetadata metadata) {
    String transport =
        (String)
            metadata
                .getAnnotationAttributes(ConditionalOnOutboxTransport.class.getName())
                .get("value");
    String type = context.getEnvironment().getProperty(TYPE_KEY);
    if (type != null && !type.isBlank()) {
      boolean matches = transport.equals(type.trim().toLowerCase(Locale.ROOT));
      return new ConditionOutcome(matches, TYPE_KEY + " is '" + type + "'");
    }
    return outcomeFromArtifacts(transport, context.getClassLoader());
  }

  private ConditionOutcome outcomeFromArtifacts(String transport, ClassLoader classLoader) {
    List<String> present =
        artifactMarkers.entrySet().stream()
            .filter(marker -> ClassUtils.isPresent(marker.getValue(), classLoader))
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    if (present.size() > 1) {
      throw new IllegalStateException(
          "Both outbox transports are on the classpath "
              + present
              + ": set "
              + TYPE_KEY
              + " to 'rabbit' or 'kafka'");
    }
    return new ConditionOutcome(
        present.contains(transport), TYPE_KEY + " unset; transport artifacts " + present);
  }
}
