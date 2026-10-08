package io.codehunters.audit.persistence;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.transaction.xa.XAResource;

/** A JTA transaction manager that records its calls and supports one suspended transaction. */
final class RecordingJta implements UserTransaction, TransactionManager {

  private final List<String> calls = new CopyOnWriteArrayList<>();
  private int status = Status.STATUS_NO_TRANSACTION;
  private int suspendedStatus = Status.STATUS_NO_TRANSACTION;

  List<String> calls() {
    return calls;
  }

  boolean isActive() {
    return status == Status.STATUS_ACTIVE;
  }

  @Override
  public void begin() {
    calls.add("begin");
    status = Status.STATUS_ACTIVE;
  }

  @Override
  public void commit() {
    calls.add("commit");
    status = Status.STATUS_NO_TRANSACTION;
  }

  @Override
  public void rollback() {
    calls.add("rollback");
    status = Status.STATUS_NO_TRANSACTION;
  }

  @Override
  public void setRollbackOnly() {
    calls.add("setRollbackOnly");
  }

  @Override
  public int getStatus() {
    return status;
  }

  @Override
  public void setTransactionTimeout(int seconds) {}

  @Override
  public Transaction getTransaction() {
    return new Suspended();
  }

  @Override
  public Transaction suspend() {
    calls.add("suspend");
    suspendedStatus = status;
    status = Status.STATUS_NO_TRANSACTION;
    return new Suspended();
  }

  @Override
  public void resume(Transaction transaction) {
    calls.add("resume");
    status = suspendedStatus;
  }

  /** The handle of a suspended transaction; never driven directly. */
  private static final class Suspended implements Transaction {

    @Override
    public void commit() {}

    @Override
    public boolean delistResource(XAResource resource, int flag) {
      return false;
    }

    @Override
    public boolean enlistResource(XAResource resource) {
      return false;
    }

    @Override
    public int getStatus() {
      return Status.STATUS_ACTIVE;
    }

    @Override
    public void registerSynchronization(Synchronization synchronization) {}

    @Override
    public void rollback() {}

    @Override
    public void setRollbackOnly() {}
  }
}
