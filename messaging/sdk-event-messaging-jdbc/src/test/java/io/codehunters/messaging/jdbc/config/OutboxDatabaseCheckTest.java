package io.codehunters.messaging.jdbc.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class OutboxDatabaseCheckTest {

  private static final String POSTGRES = DataSources.POSTGRESQL;
  private static final String H2 = "H2";
  private static final String SQL_SERVER_PRODUCT = "Microsoft SQL Server";

  @Test
  void should_accept_datasource_when_product_is_postgresql() {
    DataSource dataSource = DataSources.reporting(POSTGRES);

    assertThatCode(() -> OutboxDatabaseCheck.requirePostgres(dataSource))
        .doesNotThrowAnyException();
  }

  @Test
  void should_fail_naming_product_when_datasource_is_h2() {
    DataSource dataSource = DataSources.reporting(H2);

    assertThatThrownBy(() -> OutboxDatabaseCheck.requirePostgres(dataSource))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(H2)
        .hasMessageContaining(POSTGRES);
  }

  @Test
  void should_fail_naming_product_when_datasource_is_another_database() {
    DataSource dataSource = DataSources.reporting(SQL_SERVER_PRODUCT);

    assertThatThrownBy(() -> OutboxDatabaseCheck.requirePostgres(dataSource))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SQL_SERVER_PRODUCT);
  }

  @Test
  void should_not_fail_startup_when_database_is_unreachable() {
    DataSource dataSource = DataSources.unreachable();

    assertThatCode(() -> OutboxDatabaseCheck.requirePostgres(dataSource))
        .doesNotThrowAnyException();
  }
}
