package io.codehunters.messaging.config;

import io.codehunters.support.config.LegacyPropertyAliases;
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
 * 7.0. Runs right after the application's configuration files are loaded; a key set under the new
 * name always wins.
 */
public class OutboxLegacyProperties implements EnvironmentPostProcessor, Ordered {

  private static final LegacyPropertyAliases ALIASES =
      new LegacyPropertyAliases(
          "event-messaging", Map.of("outbox", "codehunters.outbox"), Map.of());

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
