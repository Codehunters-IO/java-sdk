package io.codehunters.totp.config;

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
 * Honours the 1.x {@code vitxo.totp.*} keys under their {@code codehunters.totp.*} names until 3.0.
 * Runs right after the application's configuration files are loaded; a key set under the new name
 * always wins.
 */
public class TotpLegacyProperties implements EnvironmentPostProcessor, Ordered {

  private static final LegacyPropertyAliases ALIASES =
      new LegacyPropertyAliases(
          "totp",
          Map.of("vitxo.totp", "codehunters.totp"),
          Map.of(),
          List.of("vitxo.totp.time-step", "vitxo.totp.digits", "vitxo.totp.algorithm"));

  private final Log log;

  /**
   * Creates the post-processor. Instantiated by Spring Boot.
   *
   * @param logFactory factory for logs written before logging is initialised
   */
  public TotpLegacyProperties(DeferredLogFactory logFactory) {
    this.log = logFactory.getLog(TotpLegacyProperties.class);
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
