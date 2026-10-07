package io.codehunters.jasper;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Runs a call on its own platform thread, so a test can see when the call starts waiting. */
final class Waiter<T> {

  private final CompletableFuture<T> result = new CompletableFuture<>();
  private final Thread thread;

  private Waiter(Supplier<T> call) {
    this.thread = Thread.ofPlatform().start(() -> run(call));
  }

  static <T> Waiter<T> start(Supplier<T> call) {
    return new Waiter<>(call);
  }

  boolean isWaiting() {
    return thread.getState() == Thread.State.WAITING;
  }

  CompletableFuture<T> result() {
    return result;
  }

  private void run(Supplier<T> call) {
    try {
      result.complete(call.get());
    } catch (Throwable e) {
      result.completeExceptionally(e);
    }
  }
}
