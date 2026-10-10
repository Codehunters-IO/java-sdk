package io.codehunters.messaging;

/**
 * Asks for an outbox dispatch soon, outside the caller's thread. The JDBC store requests one after
 * each transaction that saved outbox messages commits, so a message is published without waiting
 * for the next poll. Requests are hints: an implementation may coalesce them, and the poll still
 * picks up whatever a lost request leaves behind.
 */
@FunctionalInterface
public interface OutboxDispatchTrigger {

  /** Requests a dispatch. Never blocks on the dispatch itself and never throws. */
  void requestDispatch();

  /**
   * A trigger that ignores every request, for stores whose messages are dispatched by polling only.
   *
   * @return the shared no-op trigger
   */
  static OutboxDispatchTrigger none() {
    return NoDispatchTrigger.INSTANCE;
  }
}
