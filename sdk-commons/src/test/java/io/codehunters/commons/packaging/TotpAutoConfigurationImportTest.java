package io.codehunters.commons.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.commons.totp.config.TotpAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;

class TotpAutoConfigurationImportTest {

  @Test
  void should_register_totp_auto_configuration_when_imports_are_loaded() {
    var candidates =
        ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates();

    assertThat(candidates).contains(TotpAutoConfiguration.class.getName());
  }
}
