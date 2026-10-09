package io.codehunters.messaging.jdbc.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.SneakyThrows;

/** Mock data sources whose driver metadata reports a given database product. */
final class DataSources {

  static final String POSTGRESQL = "PostgreSQL";

  private DataSources() {}

  static DataSource postgres() {
    return reporting(POSTGRESQL);
  }

  @SneakyThrows(SQLException.class)
  static DataSource reporting(String product) {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getDatabaseProductName()).thenReturn(product);
    return dataSource;
  }

  @SneakyThrows(SQLException.class)
  static DataSource unreachable() {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));
    return dataSource;
  }
}
