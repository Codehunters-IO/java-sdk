package io.codehunters.device.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class FrameworkFreeCoreTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.codehunters.device");

  @Test
  void should_keep_the_core_free_of_frameworks_so_any_stack_can_use_it() {
    classes()
        .that()
        .resideInAnyPackage(
            "io.codehunters.device",
            "io.codehunters.device.ip",
            "io.codehunters.device.useragent",
            "io.codehunters.device.geo")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
            "java..",
            "io.codehunters.device",
            "io.codehunters.device.ip",
            "io.codehunters.device.useragent",
            "io.codehunters.device.geo")
        .check(CLASSES);
  }
}
