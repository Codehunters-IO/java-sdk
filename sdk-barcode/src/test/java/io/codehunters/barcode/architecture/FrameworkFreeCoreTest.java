package io.codehunters.barcode.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class FrameworkFreeCoreTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.codehunters.barcode");

  @Test
  void should_keep_the_core_on_the_jdk_and_zxing_so_any_stack_can_use_it() {
    classes()
        .that()
        .resideInAnyPackage(
            "io.codehunters.barcode",
            "io.codehunters.barcode.render",
            "io.codehunters.barcode.read")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
            "java..",
            "javax.imageio..",
            "com.google.zxing..",
            "io.codehunters.barcode",
            "io.codehunters.barcode.render",
            "io.codehunters.barcode.read")
        .check(CLASSES);
  }
}
