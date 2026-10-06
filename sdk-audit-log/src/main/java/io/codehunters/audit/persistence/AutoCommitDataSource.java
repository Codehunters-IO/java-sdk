package io.codehunters.audit.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * A view of a data source whose connections are never the caller's transactional connection and
 * always auto-commit. Used to write audit events outside the caller's transaction when the
 * application has no single transaction manager to start a new one.
 *
 * <p>Spring binds transactional connections to the data source instance, so connections from this
 * view are always fresh connections from the pool. Auto-commit is switched on if the pool hands out
 * manual-commit connections; pools such as HikariCP restore the setting when the connection is
 * returned. A JTA transaction still enlists these connections, so use a transaction manager there.
 *
 * @since 3.0.0
 */
public class AutoCommitDataSource extends DelegatingDataSource {

  /**
   * Creates the view.
   *
   * @param target the application's data source
   */
  public AutoCommitDataSource(DataSource target) {
    super(target);
  }

  @Override
  public Connection getConnection() throws SQLException {
    return autoCommitting(super.getConnection());
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return autoCommitting(super.getConnection(username, password));
  }

  private static Connection autoCommitting(Connection connection) throws SQLException {
    if (!connection.getAutoCommit()) {
      connection.setAutoCommit(true);
    }
    return connection;
  }
}
