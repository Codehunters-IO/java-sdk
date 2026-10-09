package io.codehunters.messaging.infrastructure.drivenadapters.jdbc;

import javax.sql.DataSource;

class SendStartedReclaimPostgresIT extends SendStartedReclaimContract {

  @Override
  DataSource dataSource() {
    return PostgresTestSupport.dataSource();
  }

  @Override
  void truncate() {
    PostgresTestSupport.truncate();
  }
}
