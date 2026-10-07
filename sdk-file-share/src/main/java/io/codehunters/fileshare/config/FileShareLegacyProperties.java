package io.codehunters.fileshare.config;

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
 * Honours the 1.x {@code vitxo.file-share.*} keys under their {@code codehunters.file-share.*}
 * names until 3.0. Runs right after the application's configuration files are loaded; a key set
 * under the new name always wins.
 */
public class FileShareLegacyProperties implements EnvironmentPostProcessor, Ordered {

  private static final LegacyPropertyAliases ALIASES =
      new LegacyPropertyAliases(
          "file-share",
          Map.of("vitxo.file-share", "codehunters.file-share"),
          Map.of(),
          List.of(
              "vitxo.file-share.enabled",
              "vitxo.file-share.base-url",
              "vitxo.file-share.connect-timeout",
              "vitxo.file-share.read-timeout"));

  private final Log log;

  /**
   * Creates the post-processor. Instantiated by Spring Boot.
   *
   * @param logFactory factory for logs written before logging is initialised
   */
  public FileShareLegacyProperties(DeferredLogFactory logFactory) {
    this.log = logFactory.getLog(FileShareLegacyProperties.class);
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
