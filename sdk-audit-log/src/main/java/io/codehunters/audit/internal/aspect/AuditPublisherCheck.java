package io.codehunters.audit.internal.aspect;

import io.codehunters.audit.AuditEventPublisherPort;
import io.codehunters.audit.internal.aspect.AuditableMethods.AuditedMethod;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Explains why no {@link AuditEventPublisherPort} exists while beans declare {@link
 * io.codehunters.audit.Auditable} methods. Without a publisher the aspect is not registered, so
 * those methods run unaudited.
 */
final class AuditPublisherCheck {

  private static final String JDBC_PREREQUISITES =
      "Spring JDBC (spring-jdbc), Spring transactions (spring-tx) or Jackson (jackson-databind)"
          + " is not on the classpath";

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
    if (audited.isEmpty()
        || beanFactory.getBeanNamesForType(AuditEventPublisherPort.class).length > 0) {
      return Optional.empty();
    }
    return Optional.of(message(reason(beanFactory), audited.getFirst()));
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
    String[] dataSources = beanFactory.getBeanNamesForType(DataSource.class);
    if (dataSources.length == 0) {
      return "the context has no DataSource bean";
    }
    if (dataSources.length > 1 && noneIsPrimary(beanFactory, dataSources)) {
      return "the context has "
          + dataSources.length
          + " DataSource beans and none is marked @Primary";
    }
    return JDBC_PREREQUISITES;
  }

  private static boolean noneIsPrimary(
      ConfigurableListableBeanFactory beanFactory, String[] dataSources) {
    return Arrays.stream(dataSources)
        .noneMatch(name -> beanFactory.getMergedBeanDefinition(name).isPrimary());
  }
}
