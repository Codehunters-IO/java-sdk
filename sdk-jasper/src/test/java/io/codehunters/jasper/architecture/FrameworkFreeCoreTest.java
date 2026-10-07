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

  @Test
  void should_keep_the_core_on_the_jdk_and_jasperreports_so_any_stack_can_use_it() {
    classes()
        .that()
        .resideInAPackage("io.codehunters.jasper")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage("java..", "net.sf.jasperreports..", "io.codehunters.jasper")
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
