package io.codehunters.audit.persistence;

import io.codehunters.audit.Auditable;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** A transactional business service whose writes the audit trail must agree with. */
class LedgerService {

  private final JdbcClient jdbc;

  LedgerService(DataSource dataSource) {
    this.jdbc = JdbcClient.create(dataSource);
  }

  @Transactional
  @Auditable(entityType = "LEDGER", action = "RECORD", entityId = "#id")
  public void record(String id) {
    insert(id);
  }

  @Transactional
  @Auditable(entityType = "LEDGER", action = "RECORD", entityId = "#id")
  public void recordThenFail(String id) {
    insert(id);
    throw new IllegalStateException("business rule violated");
  }

  @Transactional
  @Auditable(entityType = "LEDGER", action = "RECORD", entityId = "#id")
  public void recordThenBreakTheTransaction(String id) {
    insert(id);
    jdbc.sql("INSERT INTO missing_table VALUES (1)").update();
  }

  @Transactional
  @Auditable(entityType = "LEDGER", action = "RECORD", entityId = "#id")
  public void recordTwiceFailingAtCommit(String id) {
    jdbc.sql("INSERT INTO deferred_ledger (id) VALUES (:id)").param("id", id).update();
    jdbc.sql("INSERT INTO deferred_ledger (id) VALUES (:id)").param("id", id).update();
  }

  private void insert(String id) {
    jdbc.sql("INSERT INTO ledger (id) VALUES (:id)").param("id", id).update();
  }
}
