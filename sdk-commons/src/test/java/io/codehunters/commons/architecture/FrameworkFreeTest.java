package io.codehunters.commons.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class FrameworkFreeTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.codehunters.commons");

  @Test
  void should_depend_only_on_the_jdk_and_itself() {
    classes()
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage("java..", "io.codehunters.commons..")
        .check(CLASSES);
  }
}
