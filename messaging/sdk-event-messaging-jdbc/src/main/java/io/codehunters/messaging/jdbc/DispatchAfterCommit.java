package io.codehunters.messaging.jdbc;

import io.codehunters.messaging.OutboxDispatchTrigger;
import org.springframework.transaction.support.TransactionSynchronization;

// Requests a dispatch once the transaction that saved outbox messages commits; nothing on
// rollback. Equal per trigger, so a transaction registers it once however many messages it saves.
record DispatchAfterCommit(OutboxDispatchTrigger trigger) implements TransactionSynchronization {

  @Override
  public void afterCommit() {
    trigger.requestDispatch();
  }
}
