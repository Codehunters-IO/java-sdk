package io.codehunters.messaging;

// The shared trigger behind OutboxDispatchTrigger.none().
enum NoDispatchTrigger implements OutboxDispatchTrigger {
  INSTANCE;

  @Override
  public void requestDispatch() {
    // Dispatch is left to the poll.
  }
}
