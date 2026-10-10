package io.codehunters.messaging.kafka.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;

/** A mock data source whose driver reports PostgreSQL, so the outbox startup check passes. */
final class PostgresDataSource {

  private PostgresDataSource() {}

  static DataSource create() {
    try {
      return stubbed();
    } catch (SQLException e) {
      throw new IllegalStateException("Stubbing a mock data source cannot fail", e);
    }
  }

  private static DataSource stubbed() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
    return dataSource;
  }
}
