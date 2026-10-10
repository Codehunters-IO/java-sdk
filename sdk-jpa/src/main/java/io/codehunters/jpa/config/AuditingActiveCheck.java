package io.codehunters.jpa.config;

import io.codehunters.jpa.entity.AuditableEntity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.data.auditing.AuditingHandler;

/**
 * Warns once at startup when the JPA metamodel holds entities extending {@link AuditableEntity} but
 * nothing enables Spring Data JPA auditing: their audit columns would silently stay empty. Auditing
 * counts as active when an {@link AuditingHandler} bean exists, from the SDK or from the
 * application's own {@code @EnableJpaAuditing}.
 */
final class AuditingActiveCheck implements SmartInitializingSingleton {

  private static final Logger LOG = LoggerFactory.getLogger(JpaAuditingAutoConfiguration.class);
  private static final String HANDLER_BEAN = "jpaAuditingHandler";

  private final ListableBeanFactory beans;

  AuditingActiveCheck(ListableBeanFactory beans) {
    this.beans = beans;
  }

  @Override
  public void afterSingletonsInstantiated() {
    List<String> auditable = auditableEntities();
    if (auditable.isEmpty() || isAuditingActive()) {
      return;
    }
    LOG.warn(
        "{} entities extend AuditableEntity but JPA auditing is not active, so their created/updated"
            + " columns stay empty: {}. Set codehunters.jpa.auditing.enabled=true or declare"
            + " @EnableJpaAuditing",
        auditable.size(),
        auditable);
  }

  private boolean isAuditingActive() {
    return beans.containsBean(HANDLER_BEAN)
        || beans.getBeanNamesForType(AuditingHandler.class, true, false).length > 0;
  }

  private List<String> auditableEntities() {
    return beans
        .getBeanProvider(EntityManagerFactory.class)
        .orderedStream()
        .flatMap(factory -> factory.getMetamodel().getEntities().stream())
        .map(EntityType::getJavaType)
        .filter(AuditableEntity.class::isAssignableFrom)
        .map(Class::getName)
        .sorted()
        .toList();
  }
}
