package io.codehunters.messaging.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class LayerDependencyTest {

  private static final String API = "io.codehunters.messaging";

  private static final String[] FRAMEWORK_PACKAGES = {
    "org.springframework..",
    "org.springframework.amqp..",
    "javax.sql..",
    "java.sql..",
    "org.apache.kafka.."
  };

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(API);

  @Test
  void should_not_depend_on_frameworks_when_class_is_in_the_api_package() {
    noClasses()
        .that()
        .resideInAPackage(API)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(FRAMEWORK_PACKAGES)
        .check(CLASSES);
  }

  @Test
  void should_not_depend_on_frameworks_when_class_is_in_factory_or_dispatch() {
    noClasses()
        .that()
        .resideInAnyPackage(API + ".factory..", API + ".dispatch..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(FRAMEWORK_PACKAGES)
        .check(CLASSES);
  }

  @Test
  void should_not_depend_on_config_when_class_is_outside_config() {
    noClasses()
        .that()
        .resideOutsideOfPackage(API + ".config..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(API + ".config..")
        .check(CLASSES);
  }
}
