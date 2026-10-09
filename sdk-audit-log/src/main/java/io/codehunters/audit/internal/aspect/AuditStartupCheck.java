package io.codehunters.audit.internal.aspect;

import io.codehunters.audit.internal.aspect.AuditableMethods.AuditedMethod;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Startup check of the {@link io.codehunters.audit.Auditable} methods of the context: once every
 * singleton exists, it reports a context where those methods would go unaudited because no {@link
 * io.codehunters.audit.AuditEventPublisherPort} exists, naming the reason. Each problem is logged
 * as an error, or fails startup when {@code fail-on-error} is set.
 */
public final class AuditStartupCheck implements SmartInitializingSingleton {

  private static final Logger log = LoggerFactory.getLogger(AuditStartupCheck.class);

  private final ConfigurableListableBeanFactory beanFactory;
  private final boolean failOnError;

  /**
   * Creates the check.
   *
   * @param beanFactory the context's bean factory
   * @param failOnError whether a problem fails startup instead of being logged
   */
  public AuditStartupCheck(ConfigurableListableBeanFactory beanFactory, boolean failOnError) {
    this.beanFactory = beanFactory;
    this.failOnError = failOnError;
  }

  @Override
  public void afterSingletonsInstantiated() {
    List<AuditedMethod> audited = AuditableMethods.in(beanFactory);
    AuditPublisherCheck.problem(beanFactory, audited).ifPresent(this::report);
  }

  private void report(String problem) {
    if (failOnError) {
      throw new IllegalStateException(problem);
    }
    log.error(problem);
  }
}
