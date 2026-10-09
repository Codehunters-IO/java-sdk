package io.codehunters.web.device.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class FrameworkFreeCoreTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.codehunters.web.device");

  @Test
  void should_keep_the_core_free_of_frameworks_so_any_stack_can_use_it() {
    classes()
        .that()
        .resideInAnyPackage(
            "io.codehunters.web.device",
            "io.codehunters.web.device.ip",
            "io.codehunters.web.device.useragent",
            "io.codehunters.web.device.geo")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
            "java..",
            "io.codehunters.web.device",
            "io.codehunters.web.device.ip",
            "io.codehunters.web.device.useragent",
            "io.codehunters.web.device.geo")
        .check(CLASSES);
  }
}
