package io.codehunters.web.errors.config;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.MessageSource;

/**
 * Warns once at startup when the SDK's error bundle cannot be resolved through the application's
 * message source, e.g. because the application declares a {@code messageSource} that is not
 * resource-based, so lookups of the SDK's {@code error.<CODE>} keys through the context fail.
 */
final class ErrorBundleCheck implements SmartInitializingSingleton {

  /** A key only the SDK's bundle defines. */
  static final String PROBE_KEY = "error.INVALID_SORT";

  private static final Logger LOG = LoggerFactory.getLogger(ErrorBundleCheck.class);

  private final MessageSource messages;

  ErrorBundleCheck(MessageSource messages) {
    this.messages = messages;
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (!isReachable()) {
      LOG.warn(
          "sdk-web's error bundle is not reachable through the messageSource ({} does not resolve);"
              + " add '{}' to its basenames to localize the SDK's error details",
          PROBE_KEY,
          ErrorBundleRegistrar.BASENAME);
    }
  }

  private boolean isReachable() {
    String message = messages.getMessage(PROBE_KEY, null, null, Locale.ROOT);
    return message != null && !message.equals(PROBE_KEY);
  }
}
