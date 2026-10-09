package io.codehunters.commons.totp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** TOTP lives in sdk-commons: only its {@code config} package may touch Spring. */
class FrameworkFreeTotpTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.codehunters.commons.totp");

  @Test
  void should_depend_on_jdk_only_when_class_is_outside_config() {
    classes()
        .that()
        .resideOutsideOfPackage("io.codehunters.commons.totp.config..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage("java..", "javax.crypto..", "io.codehunters.commons.totp")
        .check(CLASSES);
  }
}
