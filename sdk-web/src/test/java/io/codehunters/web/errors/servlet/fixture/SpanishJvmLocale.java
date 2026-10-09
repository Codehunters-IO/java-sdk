package io.codehunters.web.errors.servlet.fixture;

import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

/**
 * Runs each test on a JVM whose default locale is Spanish, and restores the previous default
 * afterwards. Clears the JDK's bundle cache on both sides, so no lookup made under another default
 * leaks into or out of the test.
 */
public final class SpanishJvmLocale implements BeforeEachCallback, AfterEachCallback {

  private static final Namespace NAMESPACE = Namespace.create(SpanishJvmLocale.class);
  private static final String PREVIOUS = "previous";

  @Override
  public void beforeEach(ExtensionContext context) {
    context.getStore(NAMESPACE).put(PREVIOUS, Locale.getDefault());
    Locale.setDefault(Locale.of("es"));
    ResourceBundle.clearCache();
  }

  @Override
  public void afterEach(ExtensionContext context) {
    try {
      Locale.setDefault(context.getStore(NAMESPACE).remove(PREVIOUS, Locale.class));
    } finally {
      ResourceBundle.clearCache();
    }
  }
}
