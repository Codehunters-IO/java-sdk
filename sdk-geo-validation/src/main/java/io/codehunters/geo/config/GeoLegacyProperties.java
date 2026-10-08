package io.codehunters.geo.config;

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
 * Honours the 0.x {@code geo.validation.enabled} key as {@code codehunters.geo.enabled} until 2.0.
 * Runs right after the application's configuration files are loaded; a key set under the new name
 * always wins.
 */
public class GeoLegacyProperties implements EnvironmentPostProcessor, Ordered {

  private static final LegacyPropertyAliases ALIASES =
      new LegacyPropertyAliases(
          "geo",
          Map.of("geo.validation", "codehunters.geo"),
          Map.of(),
          List.of("geo.validation.enabled"));

  private final Log log;

  /**
   * Creates the post-processor. Instantiated by Spring Boot.
   *
   * @param logFactory factory for logs written before logging is initialised
   */
  public GeoLegacyProperties(DeferredLogFactory logFactory) {
    this.log = logFactory.getLog(GeoLegacyProperties.class);
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
