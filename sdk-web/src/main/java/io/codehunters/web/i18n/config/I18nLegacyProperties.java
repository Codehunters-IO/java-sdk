package io.codehunters.web.i18n.config;

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
 * Honours the 0.x {@code vitxo.i18n.*} keys under their {@code codehunters.i18n.*} names until 3.0.
 * Runs right after the application's configuration files are loaded; a key set under the new name
 * always wins.
 */
public class I18nLegacyProperties implements EnvironmentPostProcessor, Ordered {

  private static final LegacyPropertyAliases ALIASES =
      new LegacyPropertyAliases(
          "i18n",
          Map.of("vitxo.i18n", "codehunters.i18n"),
          Map.of(),
          List.of(
              "vitxo.i18n.basename",
              "vitxo.i18n.default-locale",
              "vitxo.i18n.header-name",
              "vitxo.i18n.encoding",
              "vitxo.i18n.fallback-to-system-locale"));

  private final Log log;

  /**
   * Creates the post-processor. Instantiated by Spring Boot.
   *
   * @param logFactory factory for logs written before logging is initialised
   */
  public I18nLegacyProperties(DeferredLogFactory logFactory) {
    this.log = logFactory.getLog(I18nLegacyProperties.class);
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
