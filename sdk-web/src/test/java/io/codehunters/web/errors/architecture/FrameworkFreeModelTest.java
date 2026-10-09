package io.codehunters.web.errors.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class FrameworkFreeModelTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.codehunters.web.errors");

  @Test
  void should_keep_the_error_model_free_of_frameworks_so_domain_code_can_throw_it() {
    classes()
        .that()
        .resideInAPackage("io.codehunters.web.errors")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage("java..", "io.codehunters.web.errors")
        .check(CLASSES);
  }
}
