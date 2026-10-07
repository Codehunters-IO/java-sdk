package io.codehunters.parameter.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class LayerDependencyTest {

  private static final String[] FRAMEWORK_PACKAGES = {
    "org.springframework..", "javax.sql..", "java.sql.."
  };

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.codehunters.parameter");

  @Test
  void should_not_depend_on_frameworks_when_class_is_in_domain() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(FRAMEWORK_PACKAGES)
        .check(CLASSES);
  }

  @Test
  void should_not_depend_on_frameworks_when_class_is_in_application() {
    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(FRAMEWORK_PACKAGES)
        .check(CLASSES);
  }

  @Test
  void should_not_depend_on_adapters_when_class_is_in_domain_or_application() {
    noClasses()
        .that()
        .resideInAnyPackage("..domain..", "..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure..", "..config..")
        .check(CLASSES);
  }
}
