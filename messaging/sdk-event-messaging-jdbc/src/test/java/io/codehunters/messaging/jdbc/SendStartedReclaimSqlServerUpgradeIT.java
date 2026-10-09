package io.codehunters.messaging.jdbc;

import javax.sql.DataSource;

/** Runs the reclaim contract on a 6.x-shaped table upgraded with the 7.0 add script. */
class SendStartedReclaimSqlServerUpgradeIT extends SendStartedReclaimContract {

  @Override
  DataSource dataSource() {
    return SqlServerTestSupport.upgradedDataSource();
  }

  @Override
  void truncate() {
    SqlServerTestSupport.truncateUpgraded();
  }
}
