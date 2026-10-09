package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class OutboxDialectResolverTest {

  private static final String AUTO = "auto";
  private static final String PROPERTY = "codehunters.outbox.claim.dialect";
  private static final String UNSUPPORTED = "not supported";

  private static DataSource dataSourceFor(String productName) throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getDatabaseProductName()).thenReturn(productName);
    return dataSource;
  }

  @Test
  void should_resolve_postgres_when_product_is_postgresql() throws SQLException {
    assertThat(OutboxDialectResolver.resolve(dataSourceFor("PostgreSQL"), AUTO))
        .isInstanceOf(PostgresOutboxDialect.class);
  }

  @Test
  void should_resolve_sql_server_when_product_is_microsoft_sql_server() throws SQLException {
    assertThat(OutboxDialectResolver.resolve(dataSourceFor("Microsoft SQL Server"), AUTO))
        .isInstanceOf(SqlServerOutboxDialect.class);
  }

  @Test
  void should_resolve_generic_when_product_is_h2() throws SQLException {
    assertThat(OutboxDialectResolver.resolve(dataSourceFor("H2"), "AUTO"))
        .isInstanceOf(GenericOutboxDialect.class);
  }

  @Test
  void should_fail_naming_property_and_product_when_product_is_unsupported() throws SQLException {
    DataSource oracle = dataSourceFor("Oracle");

    assertThatThrownBy(() -> OutboxDialectResolver.resolve(oracle, AUTO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(PROPERTY)
        .hasMessageContaining("Oracle");
  }

  @Test
  void should_fail_naming_property_when_metadata_cannot_be_read() throws SQLException {
    DataSource broken = mock(DataSource.class);
    when(broken.getConnection()).thenThrow(new SQLException("PII-SENTINEL"));

    assertThatThrownBy(() -> OutboxDialectResolver.resolve(broken, AUTO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(PROPERTY)
        .hasMessageNotContaining("PII-SENTINEL");
  }

  @Test
  void should_skip_metadata_when_dialect_is_configured_explicitly() {
    DataSource dataSource = mock(DataSource.class);

    OutboxDialect dialect = OutboxDialectResolver.resolve(dataSource, "PostgreSQL");

    assertThat(dialect).isInstanceOf(PostgresOutboxDialect.class);
    verifyNoInteractions(dataSource);
  }

  @Test
  void should_resolve_sql_server_when_configured_explicitly() {
    assertThat(OutboxDialectResolver.resolve(mock(DataSource.class), "sqlserver"))
        .isInstanceOf(SqlServerOutboxDialect.class);
  }

  @Test
  void should_resolve_generic_when_configured_explicitly() {
    assertThat(OutboxDialectResolver.resolve(mock(DataSource.class), "Generic"))
        .isInstanceOf(GenericOutboxDialect.class);
  }

  @Test
  void should_fail_naming_mysql_as_unsupported_when_auto_meets_mysql() throws SQLException {
    DataSource mysql = dataSourceFor("MySQL");

    assertThatThrownBy(() -> OutboxDialectResolver.resolve(mysql, AUTO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MySQL")
        .hasMessageContaining(UNSUPPORTED);
  }

  @Test
  void should_fail_naming_mariadb_as_unsupported_when_auto_meets_mariadb() throws SQLException {
    DataSource mariadb = dataSourceFor("MariaDB");

    assertThatThrownBy(() -> OutboxDialectResolver.resolve(mariadb, AUTO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MariaDB")
        .hasMessageContaining(UNSUPPORTED);
  }

  @Test
  void should_fail_naming_mysql_as_unsupported_when_generic_configured_on_mysql()
      throws SQLException {
    DataSource mysql = dataSourceFor("MySQL");

    assertThatThrownBy(() -> OutboxDialectResolver.resolve(mysql, "generic"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MySQL")
        .hasMessageContaining(UNSUPPORTED);
  }

  @Test
  void should_resolve_generic_when_configured_on_h2() throws SQLException {
    assertThat(OutboxDialectResolver.resolve(dataSourceFor("H2"), "generic"))
        .isInstanceOf(GenericOutboxDialect.class);
  }

  @Test
  void should_fail_naming_property_when_configured_value_is_unknown() {
    DataSource dataSource = mock(DataSource.class);

    assertThatThrownBy(() -> OutboxDialectResolver.resolve(dataSource, "oracle"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(PROPERTY)
        .hasMessageContaining("oracle");
  }
}
