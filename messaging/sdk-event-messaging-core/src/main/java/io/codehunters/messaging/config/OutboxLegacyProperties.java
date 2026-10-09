package io.codehunters.messaging.config;

import io.codehunters.support.config.LegacyPropertyAliases;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Honours the pre-6.0 {@code outbox.*} keys under their {@code codehunters.outbox.*} names until
 * 9.0. Runs right after the application's configuration files are loaded; a key set under the new
 * name always wins.
 */
public class OutboxLegacyProperties implements EnvironmentPostProcessor, Ordered {

  private static final LegacyPropertyAliases ALIASES =
      new LegacyPropertyAliases(
          "event-messaging",
          Map.of("outbox", "codehunters.outbox"),
          Map.of(),
          List.of(
              "outbox.enabled",
              "outbox.batch-size",
              "outbox.max-attempts",
              "outbox.task.fixed-delay",
              "outbox.task.lock-at-most",
              "outbox.task.lock-at-least",
              "outbox.task.lock-name",
              "outbox.claim.ttl-ms",
              "outbox.claim.dialect",
              "outbox.backoff.base-delay-ms",
              "outbox.backoff.max-delay-ms",
              "outbox.publisher.type",
              "outbox.publisher.kafka.send-timeout",
              "outbox.purge.retention",
              "outbox.purge.batch-size",
              "outbox.purge.fixed-delay",
              "outbox.purge.initial-delay",
              "outbox.purge.lock-name",
              "outbox.purge.lock-at-most",
              "outbox.purge.lock-at-least"));

  private final Log log;

  /**
   * Creates the post-processor. Instantiated by Spring Boot.
   *
   * @param logFactory factory for logs written before logging is initialised
   */
  public OutboxLegacyProperties(DeferredLogFactory logFactory) {
    this.log = logFactory.getLog(OutboxLegacyProperties.class);
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    ALIASES.apply(environment, log);
  }

  @Override
  public int getOrder() {
    return ConfigDataEnvironmentPostProcessor.ORDER + 1;
  }
}
