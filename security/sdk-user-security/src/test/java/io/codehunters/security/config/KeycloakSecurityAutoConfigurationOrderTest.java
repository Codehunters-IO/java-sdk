package io.codehunters.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.util.ClassUtils;

class KeycloakSecurityAutoConfigurationOrderTest {

  @Test
  void should_order_itself_against_boot_classes_that_exist() {
    String[] before =
        KeycloakSecurityAutoConfiguration.class.getAnnotation(AutoConfiguration.class).beforeName();

    assertThat(before)
        .allSatisfy(name -> assertThat(ClassUtils.isPresent(name, null)).as(name).isTrue());
  }
}
