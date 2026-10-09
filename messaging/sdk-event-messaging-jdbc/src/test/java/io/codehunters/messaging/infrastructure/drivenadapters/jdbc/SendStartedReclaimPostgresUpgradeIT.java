package io.codehunters.messaging.infrastructure.drivenadapters.jdbc;

import javax.sql.DataSource;

/** Runs the reclaim contract on a 6.x-shaped table upgraded with the 7.0 add script. */
class SendStartedReclaimPostgresUpgradeIT extends SendStartedReclaimContract {

  @Override
  DataSource dataSource() {
    return PostgresTestSupport.upgradedDataSource();
  }

  @Override
  void truncate() {
    PostgresTestSupport.truncateUpgraded();
  }
}
