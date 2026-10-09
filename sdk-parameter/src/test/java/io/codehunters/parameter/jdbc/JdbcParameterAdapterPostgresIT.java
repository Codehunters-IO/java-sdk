package io.codehunters.parameter.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.parameter.Parameter;
import io.codehunters.parameter.ParameterModule;
import io.codehunters.parameter.ParameterType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Runs the adapter against the reference DDL shipped in the jar, on a real PostgreSQL. */
@Testcontainers
class JdbcParameterAdapterPostgresIT {

  private static final String DDL = "db/migration/parameter/postgresql/parameter_create.sql";
  private static final ParameterModule ORDERS = ParameterModule.of("ORDERS");
  private static final UUID OPERATOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private static final String POSTGRES_IMAGE = "postgres:17-alpine";

  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

  private static DriverManagerDataSource dataSource;
  private JdbcClient jdbcClient;
  private JdbcParameterAdapter adapter;

  @BeforeAll
  static void createTable() {
    dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    new ResourceDatabasePopulator(new ClassPathResource(DDL)).execute(dataSource);
  }

  @BeforeEach
  void setUp() {
    jdbcClient = JdbcClient.create(dataSource);
    jdbcClient.sql("DELETE FROM parameters").update();
    adapter = new JdbcParameterAdapter(jdbcClient);
  }

  @Test
  void should_read_row_when_inserted_with_reference_ddl_defaults() {
    insert("MAX", "100");

    Parameter parameter = adapter.lookup(ORDERS, "tickets", "MAX").orElseThrow();

    assertThat(parameter.asInteger()).isEqualTo(100);
    assertThat(parameter.module()).isEqualTo(ORDERS);
    assertThat(parameter.enabled()).isTrue();
    assertThat(parameter.id()).isNotNull();
    assertThat(parameter.createdBy()).isEqualTo(OPERATOR);
    assertThat(parameter.createdAt()).isNotNull();
    assertThat(parameter.updatedAt()).isNotNull();
  }

  @Test
  void should_read_empty_submodule_when_none_is_given() {
    jdbcClient
        .sql(
            "INSERT INTO parameters (module, name, value, type, created_by)"
                + " VALUES ('ORDERS', 'VERSION', '1', 'STRING', :op)")
        .param("op", OPERATOR)
        .update();

    assertThat(adapter.lookup(ORDERS, null, "VERSION")).isPresent();
  }

  @Test
  void should_skip_row_when_soft_deleted() {
    insert("MAX", "100");
    jdbcClient.sql("UPDATE parameters SET deleted_at = now()").update();

    assertThat(adapter.lookup(ORDERS, "tickets", "MAX")).isEmpty();
  }

  @Test
  void should_reject_second_live_row_when_module_submodule_and_name_repeat() {
    insert("MAX", "100");

    assertThatThrownBy(() -> insert("MAX", "200"))
        .hasMessageContaining("ux_parameters_module_submodule_name");
  }

  @Test
  void should_reject_type_when_sdk_does_not_know_it() {
    assertThatThrownBy(
            () ->
                jdbcClient
                    .sql(
                        "INSERT INTO parameters (module, name, value, type, created_by)"
                            + " VALUES ('ORDERS', 'MAX', '1', 'XML', :op)")
                    .param("op", OPERATOR)
                    .update())
        .hasMessageContaining("ck_parameters_type");
  }

  private void insert(String name, String value) {
    jdbcClient
        .sql(
            "INSERT INTO parameters (module, submodule, name, value, type, created_by)"
                + " VALUES ('ORDERS', 'tickets', :name, :value, :type, :op)")
        .param("name", name)
        .param("value", value)
        .param("type", ParameterType.INTEGER.name())
        .param("op", OPERATOR)
        .update();
  }
}
