package io.codehunters.geo.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class FrameworkFreeCoreTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.codehunters.geo");

  @Test
  void should_keep_the_model_and_the_polygon_fence_free_of_frameworks() {
    classes()
        .that()
        .resideInAnyPackage("io.codehunters.geo", "io.codehunters.geo.polygon")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage("java..", "io.codehunters.geo", "io.codehunters.geo.polygon")
        .check(CLASSES);
  }

  @Test
  void should_keep_the_model_independent_of_any_adapter() {
    noClasses()
        .that()
        .resideInAPackage("io.codehunters.geo")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("io.codehunters.geo.polygon..", "io.codehunters.geo.postgis..")
        .check(CLASSES);
  }
}
