package io.codehunters.parameter.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.parameter.domain.exception.ParameterAccessException;
import io.codehunters.parameter.domain.exception.ParameterException;
import io.codehunters.parameter.domain.model.Parameter;
import io.codehunters.parameter.domain.model.ParameterModule;
import io.codehunters.parameter.domain.model.ParameterType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcParameterAdapterTest {

  private static final UUID OP = UUID.randomUUID();
  private static final OffsetDateTime NOW = OffsetDateTime.now();

  private JdbcDataSource dataSource;
  private JdbcClient jdbcClient;
  private JdbcParameterAdapter adapter;

  @BeforeEach
  void setUp() {
    dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:paramdb-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE");
    dataSource.setUser("sa");
    jdbcClient = JdbcClient.create((DataSource) dataSource);
    createTable("parameters");
    adapter = new JdbcParameterAdapter(jdbcClient);
  }

  @AfterEach
  void tearDown() {
    jdbcClient.sql("DROP ALL OBJECTS DELETE FILES").update();
  }

  @Test
  void lookup_returns_parameter_when_present() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    Optional<Parameter> result = adapter.lookup(ParameterModule.RAFFLES, "TICKETS", "MAX");
    assertThat(result).isPresent();
    assertThat(result.get().asLong()).isEqualTo(100L);
  }

  @Test
  void lookup_returns_empty_when_absent() {
    assertThat(adapter.lookup(ParameterModule.RAFFLES, "TICKETS", "MISSING")).isEmpty();
  }

  @Test
  void lookup_with_null_submodule_matches_empty_submodule_rows() {
    seed("GENERAL", "", "VERSION", "1", ParameterType.STRING, true, null);
    assertThat(adapter.lookup(ParameterModule.GENERAL, null, "VERSION")).isPresent();
  }

  @Test
  void lookup_excludes_disabled_rows() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, false, null);
    assertThat(adapter.lookup(ParameterModule.RAFFLES, "TICKETS", "MAX")).isEmpty();
  }

  @Test
  void lookup_excludes_softDeleted_rows() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, NOW);
    assertThat(adapter.lookup(ParameterModule.RAFFLES, "TICKETS", "MAX")).isEmpty();
  }

  @Test
  void findByModule_returns_all_when_submodule_null() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    seed("RAFFLES", "PAYMENT", "FEE", "5", ParameterType.INTEGER, true, null);
    List<Parameter> result = adapter.findByModule(ParameterModule.RAFFLES, null);
    assertThat(result).hasSize(2).extracting(Parameter::name).containsExactly("FEE", "MAX");
  }

  @Test
  void findByModule_narrows_by_submodule() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    seed("RAFFLES", "PAYMENT", "FEE", "5", ParameterType.INTEGER, true, null);
    assertThat(adapter.findByModule(ParameterModule.RAFFLES, "TICKETS"))
        .hasSize(1)
        .extracting(Parameter::name)
        .containsExactly("MAX");
  }

  @Test
  void findByModule_excludes_other_modules() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    seed("PAYMENT", "TICKETS", "FEE", "5", ParameterType.INTEGER, true, null);
    assertThat(adapter.findByModule(ParameterModule.RAFFLES, null)).hasSize(1);
  }

  @Test
  void findByModule_excludes_disabled_and_softDeleted_rows() {
    seed("RAFFLES", "TICKETS", "DISABLED", "0", ParameterType.INTEGER, false, null);
    seed("RAFFLES", "TICKETS", "DELETED", "0", ParameterType.INTEGER, true, NOW);
    seed("RAFFLES", "TICKETS", "ACTIVE", "10", ParameterType.INTEGER, true, null);
    assertThat(adapter.findByModule(ParameterModule.RAFFLES, "TICKETS"))
        .extracting(Parameter::name)
        .containsExactly("ACTIVE");
  }

  @Test
  void asMap_returns_name_to_value() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    seed("RAFFLES", "TICKETS", "MIN", "1", ParameterType.INTEGER, true, null);
    Map<String, String> map = adapter.asMap(ParameterModule.RAFFLES, "TICKETS");
    assertThat(map).containsEntry("MAX", "100").containsEntry("MIN", "1");
  }

  @Test
  void asMapByName_returns_name_to_parameter() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    Map<String, Parameter> map = adapter.asMapByName(ParameterModule.RAFFLES, "TICKETS");
    assertThat(map).hasSize(1);
    assertThat(map.get("MAX").asLong()).isEqualTo(100L);
  }

  @Test
  void findByModules_returns_rows_for_all_listed_modules() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    seed("PAYMENT", "FEE", "RATE", "5", ParameterType.INTEGER, true, null);
    seed("NOTIFICATIONS", "EMAIL", "HOST", "smtp", ParameterType.STRING, true, null);
    List<Parameter> result =
        adapter.findByModules(List.of(ParameterModule.RAFFLES, ParameterModule.PAYMENT));
    assertThat(result)
        .extracting(p -> p.module() + ":" + p.name())
        .containsExactlyInAnyOrder("PAYMENT:RATE", "RAFFLES:MAX");
  }

  @Test
  void findByModules_excludes_disabled_and_softDeleted_rows() {
    seed("RAFFLES", "T", "DISABLED", "0", ParameterType.INTEGER, false, null);
    seed("RAFFLES", "T", "DELETED", "0", ParameterType.INTEGER, true, NOW);
    seed("RAFFLES", "T", "ACTIVE", "1", ParameterType.INTEGER, true, null);
    assertThat(adapter.findByModules(List.of(ParameterModule.RAFFLES)))
        .extracting(Parameter::name)
        .containsExactly("ACTIVE");
  }

  @Test
  void findByModules_returns_empty_for_empty_collection() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    assertThat(adapter.findByModules(List.of())).isEmpty();
  }

  @Test
  void findByModules_returns_empty_for_null() {
    assertThat(adapter.findByModules(null)).isEmpty();
  }

  @Test
  void findByModuleAndSubmodules_returns_rows_for_listed_submodules() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    seed("RAFFLES", "PAYMENT", "FEE", "5", ParameterType.INTEGER, true, null);
    seed("RAFFLES", "OTHER", "X", "1", ParameterType.INTEGER, true, null);
    List<Parameter> result =
        adapter.findByModuleAndSubmodules(ParameterModule.RAFFLES, Set.of("TICKETS", "PAYMENT"));
    assertThat(result)
        .extracting(p -> p.submodule() + ":" + p.name())
        .containsExactlyInAnyOrder("TICKETS:MAX", "PAYMENT:FEE");
  }

  @Test
  void findByModuleAndSubmodules_excludes_other_modules() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    seed("PAYMENT", "TICKETS", "FEE", "5", ParameterType.INTEGER, true, null);
    assertThat(adapter.findByModuleAndSubmodules(ParameterModule.RAFFLES, List.of("TICKETS")))
        .hasSize(1);
  }

  @Test
  void findByModuleAndSubmodules_returns_empty_for_empty_collection() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    assertThat(adapter.findByModuleAndSubmodules(ParameterModule.RAFFLES, List.of())).isEmpty();
  }

  @Test
  void findByModuleAndSubmodules_returns_empty_for_null() {
    assertThat(adapter.findByModuleAndSubmodules(ParameterModule.RAFFLES, null)).isEmpty();
  }

  @Test
  void should_keep_a_null_value_in_asMap() {
    seed("RAFFLES", "TICKETS", "EMPTY", null, ParameterType.STRING, true, null);
    assertThat(adapter.asMap(ParameterModule.RAFFLES, "TICKETS")).containsEntry("EMPTY", null);
  }

  @Test
  void should_keep_the_name_order_in_asMap() {
    seed("RAFFLES", "TICKETS", "B", "2", ParameterType.INTEGER, true, null);
    seed("RAFFLES", "TICKETS", "C", "3", ParameterType.INTEGER, true, null);
    seed("RAFFLES", "TICKETS", "A", "1", ParameterType.INTEGER, true, null);
    assertThat(adapter.asMap(ParameterModule.RAFFLES, "TICKETS").keySet())
        .containsExactly("A", "B", "C");
  }

  @Test
  void should_keep_the_name_order_in_asMapByName() {
    seed("RAFFLES", "TICKETS", "B", "2", ParameterType.INTEGER, true, null);
    seed("RAFFLES", "TICKETS", "A", "1", ParameterType.INTEGER, true, null);
    assertThat(adapter.asMapByName(ParameterModule.RAFFLES, "TICKETS").keySet())
        .containsExactly("A", "B");
  }

  @Test
  void should_reject_a_name_shared_by_two_submodules_in_asMap() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    seed("RAFFLES", "PAYMENT", "MAX", "5", ParameterType.INTEGER, true, null);
    assertThatThrownBy(() -> adapter.asMap(ParameterModule.RAFFLES, null))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("MAX");
  }

  @Test
  void should_reject_a_name_shared_by_two_submodules_in_asMapByName() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    seed("RAFFLES", "PAYMENT", "MAX", "5", ParameterType.INTEGER, true, null);
    assertThatThrownBy(() -> adapter.asMapByName(ParameterModule.RAFFLES, null))
        .isInstanceOf(ParameterException.class);
  }

  @Test
  void should_treat_a_blank_submodule_as_empty_in_lookup() {
    seed("GENERAL", "", "VERSION", "1", ParameterType.STRING, true, null);
    assertThat(adapter.lookup(ParameterModule.GENERAL, "  ", "VERSION")).isPresent();
  }

  @Test
  void should_treat_a_blank_submodule_as_empty_in_findByModule() {
    seed("GENERAL", "", "VERSION", "1", ParameterType.STRING, true, null);
    seed("GENERAL", "UI", "THEME", "dark", ParameterType.STRING, true, null);
    assertThat(adapter.findByModule(ParameterModule.GENERAL, " "))
        .extracting(Parameter::name)
        .containsExactly("VERSION");
  }

  @Test
  void should_read_the_configured_table() {
    createTable("app_parameters");
    jdbcClient
        .sql(
            "INSERT INTO app_parameters (id, module, submodule, name, value, type, enabled,"
                + " created_by, created_at, updated_at) VALUES"
                + " (:id, 'GENERAL', '', 'VERSION', '2', 'STRING', TRUE, :op, :now, :now)")
        .param("id", UUID.randomUUID())
        .param("op", OP)
        .param("now", NOW)
        .update();
    var custom = new JdbcParameterAdapter(jdbcClient, "app_parameters");
    assertThat(custom.lookup(ParameterModule.GENERAL, null, "VERSION")).isPresent();
  }

  @Test
  void should_reject_a_table_name_that_is_not_an_identifier() {
    assertThatThrownBy(() -> new JdbcParameterAdapter(jdbcClient, "parameters; DROP TABLE x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_accept_a_schema_qualified_table_name() {
    assertThat(new JdbcParameterAdapter(jdbcClient, "dbo.parameters")).isNotNull();
  }

  @Test
  void should_return_an_unmodifiable_list_from_findByModule() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    List<Parameter> result = adapter.findByModule(ParameterModule.RAFFLES, null);
    assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void should_return_an_unmodifiable_list_from_findByModules() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    List<Parameter> result = adapter.findByModules(List.of(ParameterModule.RAFFLES));
    assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void should_return_an_unmodifiable_list_from_findByModuleAndSubmodules() {
    seed("RAFFLES", "TICKETS", "MAX", "100", ParameterType.INTEGER, true, null);
    List<Parameter> result =
        adapter.findByModuleAndSubmodules(ParameterModule.RAFFLES, List.of("TICKETS"));
    assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void wraps_DataAccessException_into_ParameterAccessException() {
    jdbcClient.sql("DROP TABLE parameters").update();
    assertThatThrownBy(() -> adapter.lookup(ParameterModule.RAFFLES, "TICKETS", "MAX"))
        .isInstanceOf(ParameterAccessException.class);
  }

  @Test
  void should_map_a_module_unknown_to_the_sdk_and_tolerate_an_unknown_type() {
    UUID id = UUID.randomUUID();
    jdbcClient
        .sql(
            "INSERT INTO parameters (id, module, submodule, name, value, type, enabled,"
                + " description, created_by, created_at, updated_by, updated_at, deleted_at) VALUES"
                + " (:id, 'UNKNOWN_MOD', '', 'X', '1', 'WEIRD_TYPE', TRUE, NULL, :op, :now, :op,"
                + " :now, NULL)")
        .param("id", id)
        .param("op", OP)
        .param("now", NOW)
        .update();
    List<Parameter> all =
        jdbcClient.sql("SELECT * FROM parameters").query(new ParameterRowMapper()).list();
    assertThat(all).hasSize(1);
    assertThat(all.get(0).module()).isEqualTo(ParameterModule.of("UNKNOWN_MOD"));
    assertThat(all.get(0).type()).isNull();
  }

  private void seed(
      String module,
      String submodule,
      String name,
      String value,
      ParameterType type,
      boolean enabled,
      OffsetDateTime deletedAt) {
    jdbcClient
        .sql(
            "INSERT INTO parameters (id, module, submodule, name, value, type, enabled,"
                + " description, created_by, created_at, updated_by, updated_at, deleted_at) VALUES"
                + " (:id, :module, :submodule, :name, :value, :type, :enabled, :description, :op,"
                + " :now, :op, :now, :deletedAt)")
        .param("id", UUID.randomUUID())
        .param("module", module)
        .param("submodule", submodule)
        .param("name", name)
        .param("value", value)
        .param("type", type.name())
        .param("enabled", enabled)
        .param("description", "test")
        .param("op", OP)
        .param("now", NOW)
        .param("deletedAt", deletedAt)
        .update();
  }

  private void createTable(String table) {
    jdbcClient
        .sql(
            "CREATE TABLE "
                + table
                + " ("
                + "id UUID PRIMARY KEY,"
                + "module VARCHAR(64) NOT NULL,"
                + "submodule VARCHAR(64) NOT NULL,"
                + "name VARCHAR(128) NOT NULL,"
                + "value CLOB,"
                + "type VARCHAR(16) NOT NULL,"
                + "enabled BOOLEAN NOT NULL,"
                + "description VARCHAR(500),"
                + "created_by UUID NOT NULL,"
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL,"
                + "updated_by UUID,"
                + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL,"
                + "deleted_at TIMESTAMP WITH TIME ZONE)")
        .update();
  }
}
