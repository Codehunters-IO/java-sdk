package io.codehunters.messaging.jdbc;

import javax.sql.DataSource;

class SendStartedReclaimSqlServerIT extends SendStartedReclaimContract {

  @Override
  DataSource dataSource() {
    return SqlServerTestSupport.dataSource();
  }

  @Override
  void truncate() {
    SqlServerTestSupport.truncate();
  }
}
