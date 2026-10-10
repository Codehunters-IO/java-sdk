package io.codehunters.jpa.fixture;

import io.codehunters.jpa.config.JpaAuditingAutoConfiguration;
import io.codehunters.jpa.fixture.plain.Note;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Context runners wiring Boot's JPA auto-configuration and the SDK against a test database. */
public final class JpaFixtures {

  private JpaFixtures() {}

  /** Scans the fixture entities and repositories. */
  @Configuration(proxyBeanMethods = false)
  @EntityScan(basePackageClasses = Order.class)
  @EnableJpaRepositories(basePackageClasses = Order.class)
  public static class FixtureApplication {}

  /** Scans only entities that do not extend {@code AuditableEntity}. */
  @Configuration(proxyBeanMethods = false)
  @EntityScan(basePackageClasses = Note.class)
  @EnableJpaRepositories(basePackageClasses = Note.class)
  public static class PlainApplication {}

  public static ApplicationContextRunner h2() {
    return h2(FixtureApplication.class);
  }

  public static ApplicationContextRunner h2(Class<?> application) {
    return runner(application)
        .withPropertyValues(
            "spring.datasource.url=jdbc:h2:mem:jpa-${random.uuid};DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa");
  }

  public static ApplicationContextRunner postgres() {
    return runner(FixtureApplication.class)
        .withPropertyValues(
            "spring.datasource.url=" + PostgresDatabase.jdbcUrl(),
            "spring.datasource.username=" + PostgresDatabase.username(),
            "spring.datasource.password=" + PostgresDatabase.password());
  }

  private static ApplicationContextRunner runner(Class<?> application) {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                DataJpaRepositoriesAutoConfiguration.class,
                TransactionAutoConfiguration.class,
                JpaAuditingAutoConfiguration.class))
        .withUserConfiguration(application)
        .withPropertyValues("spring.jpa.hibernate.ddl-auto=create-drop");
  }
}
