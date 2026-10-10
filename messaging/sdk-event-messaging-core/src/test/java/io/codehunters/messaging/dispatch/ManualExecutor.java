package io.codehunters.messaging.dispatch;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Executor that queues tasks until the test runs them on its own thread, and can refuse the next
 * submission as a shut-down executor would.
 */
final class ManualExecutor implements Executor {

  private final Deque<Runnable> queue = new ArrayDeque<>();
  private int submitted;
  private boolean rejectNext;

  void rejectNext() {
    rejectNext = true;
  }

  int submitted() {
    return submitted;
  }

  void runAll() {
    while (!queue.isEmpty()) {
      queue.poll().run();
    }
  }

  @Override
  public void execute(Runnable task) {
    if (rejectNext) {
      rejectNext = false;
      throw new RejectedExecutionException("shut down");
    }
    submitted++;
    queue.add(task);
  }
}
