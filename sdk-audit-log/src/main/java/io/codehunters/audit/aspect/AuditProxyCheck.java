package io.codehunters.audit.aspect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Startup check that the {@link io.codehunters.audit.aspect.AuditAspect} is actually applied.
 * Without an AspectJ auto-proxy creator ({@code spring.aop.auto=false}, an excluded {@code
 * AopAutoConfiguration}, or a plain Spring context) no {@link io.codehunters.audit.Auditable}
 * method is intercepted and no event is ever written. That gap is logged as a warning, or fails
 * startup when {@code fail-on-error} is set.
 */
public final class AuditProxyCheck implements SmartInitializingSingleton {

  static final String MESSAGE =
      "Auditing is inactive: no AspectJ auto-proxy creator is registered, so @Auditable methods are"
          + " not intercepted. Enable spring.aop.auto or @EnableAspectJAutoProxy.";

  private static final Logger log = LoggerFactory.getLogger(AuditProxyCheck.class);

  private final ListableBeanFactory beanFactory;
  private final boolean failOnError;

  public AuditProxyCheck(ListableBeanFactory beanFactory, boolean failOnError) {
    this.beanFactory = beanFactory;
    this.failOnError = failOnError;
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (hasAspectJProxyCreator()) {
      return;
    }
    if (failOnError) {
      throw new IllegalStateException(MESSAGE);
    }
    log.warn(MESSAGE);
  }

  private boolean hasAspectJProxyCreator() {
    String name = AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME;
    return beanFactory.containsBean(name)
        && beanFactory.isTypeMatch(name, AnnotationAwareAspectJAutoProxyCreator.class);
  }
}
