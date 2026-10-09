package io.codehunters.messaging.jdbc.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class OutboxDatabaseCheckTest {

  private static final String POSTGRES = "PostgreSQL";
  private static final String H2 = "H2";
  private static final String SQL_SERVER_PRODUCT = "Microsoft SQL Server";

  @Test
  void should_accept_datasource_when_product_is_postgresql() throws SQLException {
    DataSource dataSource = reporting(POSTGRES);

    assertThatCode(() -> OutboxDatabaseCheck.requirePostgres(dataSource))
        .doesNotThrowAnyException();
  }

  @Test
  void should_fail_naming_product_when_datasource_is_h2() throws SQLException {
    DataSource dataSource = reporting(H2);

    assertThatThrownBy(() -> OutboxDatabaseCheck.requirePostgres(dataSource))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(H2)
        .hasMessageContaining(POSTGRES);
  }

  @Test
  void should_fail_naming_product_when_datasource_is_another_database() throws SQLException {
    DataSource dataSource = reporting(SQL_SERVER_PRODUCT);

    assertThatThrownBy(() -> OutboxDatabaseCheck.requirePostgres(dataSource))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SQL_SERVER_PRODUCT);
  }

  @Test
  void should_not_fail_startup_when_database_is_unreachable() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

    assertThatCode(() -> OutboxDatabaseCheck.requirePostgres(dataSource))
        .doesNotThrowAnyException();
  }

  private static DataSource reporting(String product) throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getDatabaseProductName()).thenReturn(product);
    return dataSource;
  }
}
