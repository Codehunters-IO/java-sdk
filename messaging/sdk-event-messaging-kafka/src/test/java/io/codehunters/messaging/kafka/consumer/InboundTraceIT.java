package io.codehunters.messaging.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.kafka.consumer.InboundListenerConfig.Behaviour;
import io.codehunters.messaging.kafka.consumer.config.EventMessagingKafkaConsumerAutoConfiguration;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.kafka.KafkaContainer;

class InboundTraceIT {

  private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.1");
  private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(30);
  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
  private static final String ENVELOPE = "{\"header\":{},\"body\":{}}";

  private static InboundBroker broker;
  private static SdkTracerProvider provider;
  private static Tracer tracer;
  private static ObservationRegistry observations;

  @BeforeAll
  static void start() {
    KAFKA.start();
    broker = new InboundBroker(KAFKA.getBootstrapServers());
    provider = SdkTracerProvider.builder().build();
    OtelCurrentTraceContext current = new OtelCurrentTraceContext();
    io.opentelemetry.api.trace.Tracer otel = provider.get("inbound-it");
    tracer =
        new OtelTracer(
            otel, current, event -> {}, new OtelBaggageManager(current, List.of(), List.of()));
    OtelPropagator propagator =
        new OtelPropagator(
            ContextPropagators.create(W3CTraceContextPropagator.getInstance()), otel);
    observations = ObservationRegistry.create();
    observations
        .observationConfig()
        .observationHandler(
            new ObservationHandler.FirstMatchingCompositeObservationHandler(
                new PropagatingReceiverTracingObservationHandler<>(tracer, propagator),
                new DefaultTracingObservationHandler(tracer)));
  }

  @AfterAll
  static void stop() {
    provider.close();
    KAFKA.stop();
  }

  @Test
  void should_continue_producer_trace_on_main_topic_and_retry_hop_when_listener_observes()
      throws Exception {
    String topic = "inbound-trace-it-" + UUID.randomUUID();
    broker.createTopology(topic);
    TraceRecorder recorder = new TraceRecorder(topic);

    application(topic, recorder)
        .run(
            context -> {
              broker.send(
                  topic, ENVELOPE, Map.of(MessagePublisherPort.TRACEPARENT_HEADER, TRACEPARENT));

              assertThat(recorder.awaitHops(2, RECEIVE_TIMEOUT))
                  .containsExactly(topic + "=" + TRACE_ID, topic + "-retry-0=" + TRACE_ID);
            });
  }

  private static ApplicationContextRunner application(String topic, Behaviour behaviour) {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                KafkaAutoConfiguration.class, EventMessagingKafkaConsumerAutoConfiguration.class))
        .withUserConfiguration(InboundListenerConfig.class)
        .withPropertyValues(
            "spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.listener.observation-enabled=true",
            "it.topic=" + topic,
            "it.group=inbound-trace-it-" + UUID.randomUUID())
        .withBean(ObservationRegistry.class, () -> observations)
        .withBean(Behaviour.class, () -> behaviour);
  }

  /**
   * Records {@code <topic>=<current trace id>} for each delivery and fails the first one, on the
   * main topic, so the record hops to {@code -retry-0}.
   */
  static final class TraceRecorder implements Behaviour {

    private final String mainTopic;
    private final List<String> hops = new CopyOnWriteArrayList<>();

    TraceRecorder(String mainTopic) {
      this.mainTopic = mainTopic;
    }

    @Override
    public void on(ConsumerRecord<String, String> record) {
      Span span = tracer.currentSpan();
      hops.add(record.topic() + "=" + (span == null ? "none" : span.context().traceId()));
      if (record.topic().equals(mainTopic)) {
        throw new IllegalArgumentException("first delivery fails");
      }
    }

    List<String> awaitHops(int count, Duration timeout) throws InterruptedException {
      long deadline = System.nanoTime() + timeout.toNanos();
      while (hops.size() < count && System.nanoTime() < deadline) {
        Thread.sleep(100);
      }
      return List.copyOf(hops);
    }
  }
}
