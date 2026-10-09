package io.codehunters.parameter.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class LayerDependencyTest {

  private static final String API_PACKAGE = "io.codehunters.parameter";

  private static final String[] FRAMEWORK_PACKAGES = {
    "org.springframework..", "javax.sql..", "java.sql.."
  };

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(API_PACKAGE);

  @Test
  void should_not_depend_on_frameworks_when_class_is_in_api_package() {
    noClasses()
        .that()
        .resideInAPackage(API_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(FRAMEWORK_PACKAGES)
        .check(CLASSES);
  }

  @Test
  void should_not_depend_on_implementations_when_class_is_in_api_package() {
    noClasses()
        .that()
        .resideInAPackage(API_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            API_PACKAGE + ".jdbc..",
            API_PACKAGE + ".cache..",
            API_PACKAGE + ".internal..",
            API_PACKAGE + ".config..")
        .check(CLASSES);
  }
}
