package io.codehunters.web.errors.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.AbstractResourceBasedMessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * Appends the SDK's error bundle to the application's {@code messageSource} (Boot's, configured by
 * {@code spring.messages.*}, or the application's own resource-based one). Appended last, so the
 * application's bundles win for any key they define.
 */
final class ErrorBundleRegistrar implements BeanPostProcessor {

  /** The SDK's error bundle, as a class path base name. */
  static final String BASENAME = "io/codehunters/web/errors/client-input";

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) {
    if (AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME.equals(beanName)
        && bean instanceof AbstractResourceBasedMessageSource messages) {
      messages.addBasenames(basenameFor(messages));
    }
    return bean;
  }

  private static String basenameFor(AbstractResourceBasedMessageSource messages) {
    return messages instanceof ReloadableResourceBundleMessageSource
        ? "classpath:" + BASENAME
        : BASENAME;
  }
}
