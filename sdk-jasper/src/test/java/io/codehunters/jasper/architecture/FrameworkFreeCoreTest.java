package io.codehunters.jasper.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class FrameworkFreeCoreTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.codehunters.jasper");

  private static final String[] CORE = {
    "io.codehunters.jasper",
    "io.codehunters.jasper.internal.template",
    "io.codehunters.jasper.internal.fill",
    "io.codehunters.jasper.internal.export"
  };

  // Caffeine is a plain library with no framework of its own, so it keeps the core stack-neutral.
  @Test
  void should_depend_only_on_the_jdk_jasperreports_and_caffeine_when_outside_config_and_metrics() {
    classes()
        .that()
        .resideInAnyPackage(CORE)
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
            "java..",
            "net.sf.jasperreports..",
            "com.github.benmanes.caffeine..",
            "io.codehunters.jasper",
            "io.codehunters.jasper.internal.template",
            "io.codehunters.jasper.internal.fill",
            "io.codehunters.jasper.internal.export")
        .check(CLASSES);
  }

  @Test
  void should_keep_metrics_free_of_spring() {
    classes()
        .that()
        .resideInAPackage("io.codehunters.jasper.metrics")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage("java..", "io.micrometer..", "io.codehunters.jasper..")
        .check(CLASSES);
  }
}
