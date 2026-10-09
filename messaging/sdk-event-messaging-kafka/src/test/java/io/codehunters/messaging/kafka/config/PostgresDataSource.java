package io.codehunters.messaging.kafka.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.SneakyThrows;

/** A mock data source whose driver reports PostgreSQL, so the outbox startup check passes. */
final class PostgresDataSource {

  private PostgresDataSource() {}

  @SneakyThrows(SQLException.class)
  static DataSource create() {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
    return dataSource;
  }
}
