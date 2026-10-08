package io.codehunters.totp.architecture;

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
          .importPackages("io.codehunters.totp");

  @Test
  void should_keep_the_totp_core_on_the_jdk_only() {
    classes()
        .that()
        .resideOutsideOfPackage("io.codehunters.totp.config..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage("java..", "javax.crypto..", "io.codehunters.totp")
        .check(CLASSES);
  }

  @Test
  void should_keep_the_helpers_independent_of_totp() {
    classes()
        .that()
        .resideInAPackage("io.codehunters.commons..")
        .should()
        .onlyDependOnClassesThat()
        .resideOutsideOfPackage("io.codehunters.totp..")
        .check(
            new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.codehunters.commons"));
  }
}
