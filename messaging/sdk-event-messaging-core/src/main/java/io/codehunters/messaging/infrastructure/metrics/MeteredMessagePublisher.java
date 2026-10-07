package io.codehunters.messaging.infrastructure.metrics;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.domain.model.OutboxMessage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * {@link MessagePublisherPort} decorator that times every publish with the {@value #TIMER} timer,
 * tagged with {@code outcome} ({@code success} or {@code failure}) and {@code exception} (the
 * failure's simple class name, or {@code none}). Failures are rethrown unchanged.
 */
public final class MeteredMessagePublisher implements MessagePublisherPort {

  /** Name of the publish timer. */
  public static final String TIMER = "outbox.publish";

  private static final String NONE = "none";

  private final MessagePublisherPort delegate;
  private final MeterRegistry registry;

  /**
   * Creates the decorator.
   *
   * @param delegate the publisher that actually sends the messages
   * @param registry the registry the timer is recorded in
   */
  public MeteredMessagePublisher(MessagePublisherPort delegate, MeterRegistry registry) {
    this.delegate = delegate;
    this.registry = registry;
  }

  @Override
  public void publish(String topic, String routingKey, String payload) {
    timed(() -> delegate.publish(topic, routingKey, payload));
  }

  @Override
  public void publish(String topic, String payload) {
    timed(() -> delegate.publish(topic, payload));
  }

  @Override
  public void publish(OutboxMessage<String> message) {
    timed(() -> delegate.publish(message));
  }

  private void timed(Runnable publish) {
    Timer.Sample sample = Timer.start(registry);
    try {
      publish.run();
    } catch (RuntimeException e) {
      sample.stop(timer("failure", e.getClass().getSimpleName()));
      throw e;
    }
    sample.stop(timer("success", NONE));
  }

  private Timer timer(String outcome, String exception) {
    return Timer.builder(TIMER)
        .description("Time to publish one outbox message to the broker")
        .tag("outcome", outcome)
        .tag("exception", exception)
        .register(registry);
  }
}
