package io.codehunters.jpa.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class LayeringTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.codehunters.jpa");

  @Test
  void should_keep_spring_wiring_in_the_config_package() {
    noClasses()
        .that()
        .resideOutsideOfPackage("io.codehunters.jpa.config")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework.boot.autoconfigure..", "org.springframework.context..")
        .check(CLASSES);
  }

  @Test
  void should_keep_base_entities_free_of_web_security_and_json() {
    classes()
        .that()
        .resideInAPackage("io.codehunters.jpa.domain")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
            "java..",
            "jakarta.persistence..",
            "org.hibernate..",
            "org.springframework.data..",
            "io.codehunters.jpa.domain")
        .check(CLASSES);
  }

  @Test
  void should_keep_page_responses_free_of_jpa() {
    noClasses()
        .that()
        .resideInAPackage("io.codehunters.jpa.pagination")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
        .check(CLASSES);
  }
}
