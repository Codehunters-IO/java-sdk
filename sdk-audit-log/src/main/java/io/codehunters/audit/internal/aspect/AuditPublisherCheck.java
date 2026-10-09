package io.codehunters.audit.internal.aspect;

import io.codehunters.audit.AuditEventPublisherPort;
import io.codehunters.audit.internal.aspect.AuditableMethods.AuditedMethod;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Explains why no {@link AuditEventPublisherPort} exists while beans declare {@link
 * io.codehunters.audit.Auditable} methods. Without a publisher the aspect is not registered, so
 * those methods run unaudited. Beans of parent contexts count, as they do for the aspect's
 * condition.
 */
final class AuditPublisherCheck {

  private static final String JDBC_NOT_CONFIGURED =
      "the JDBC publisher is not configured (missing classes or auto-configuration excluded; it"
          + " needs spring-jdbc, spring-tx and jackson-databind)";

  private AuditPublisherCheck() {}

  /**
   * Describes the gap, if there is one.
   *
   * @param beanFactory the context's bean factory
   * @param audited the context's audited methods
   * @return why auditing is inactive, or empty when a publisher exists or nothing is audited
   */
  static Optional<String> problem(
      ConfigurableListableBeanFactory beanFactory, List<AuditedMethod> audited) {
    if (audited.isEmpty() || hasPublisher(beanFactory)) {
      return Optional.empty();
    }
    return Optional.of(message(reason(beanFactory), audited.getFirst()));
  }

  /**
   * Whether the context or one of its parents has a publisher.
   *
   * @param beanFactory the context's bean factory
   * @return {@code true} when a publisher exists
   */
  static boolean hasPublisher(ConfigurableListableBeanFactory beanFactory) {
    return names(beanFactory, AuditEventPublisherPort.class).length > 0;
  }

  private static String[] names(ConfigurableListableBeanFactory beanFactory, Class<?> type) {
    return BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, type);
  }

  private static String message(String reason, AuditedMethod example) {
    return "Auditing is inactive: @Auditable methods exist (for example "
        + example.label()
        + "), but there is no AuditEventPublisherPort, because "
        + reason
        + ". No audit event is written. Define an AuditEventPublisherPort bean, or give the JDBC"
        + " publisher a single DataSource candidate.";
  }

  private static String reason(ConfigurableListableBeanFactory beanFactory) {
    String[] dataSources = names(beanFactory, DataSource.class);
    if (dataSources.length == 0) {
      return "the context has no DataSource bean";
    }
    if (dataSources.length > 1 && noneIsPrimary(beanFactory, dataSources)) {
      return "the context has "
          + dataSources.length
          + " DataSource beans and none is marked @Primary";
    }
    return JDBC_NOT_CONFIGURED;
  }

  private static boolean noneIsPrimary(
      ConfigurableListableBeanFactory beanFactory, String[] dataSources) {
    return Arrays.stream(dataSources).noneMatch(name -> isPrimary(beanFactory, name));
  }

  /** A bean registered as a plain singleton has no definition, so it is not primary. */
  private static boolean isPrimary(ConfigurableListableBeanFactory beanFactory, String name) {
    try {
      return beanFactory.getMergedBeanDefinition(name).isPrimary();
    } catch (NoSuchBeanDefinitionException ex) {
      return false;
    }
  }
}
