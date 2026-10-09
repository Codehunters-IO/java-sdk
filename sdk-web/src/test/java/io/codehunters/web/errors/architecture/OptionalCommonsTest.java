package io.codehunters.web.errors.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * sdk-commons is optional for sdk-web: only the class loaded behind the presence check may touch
 * it, so an application without sdk-commons never resolves its types.
 */
class OptionalCommonsTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.codehunters.web");

  @Test
  void should_reference_sdk_commons_only_from_the_class_behind_the_presence_check() {
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName("io.codehunters.web.errors.servlet.ClientInputs")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("io.codehunters.commons..")
        .check(CLASSES);
  }
}
