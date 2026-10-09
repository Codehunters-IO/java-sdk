package io.codehunters.audit.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ReferenceDdlPackagingTest {

  @Test
  void should_not_ship_a_flyway_versioned_migration() {
    assertThat(new ClassPathResource("db/migration/V1__create_audit_event_table.sql").exists())
        .isFalse();
  }

  @Test
  void should_ship_reference_ddl_for_postgres() {
    assertThat(new ClassPathResource(ReferenceDdlFixtures.POSTGRES_DDL).exists()).isTrue();
  }
}
