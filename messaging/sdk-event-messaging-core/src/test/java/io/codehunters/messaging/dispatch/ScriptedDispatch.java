package io.codehunters.messaging.dispatch;

import io.codehunters.messaging.SendOutboxMessagesPort;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dispatch port that answers scripted "claim was full" results (then {@code false}), counts its
 * runs and the most runs seen at once, and can run a hook during its first run or fail.
 */
final class ScriptedDispatch implements SendOutboxMessagesPort {

  private final Deque<Boolean> results = new ArrayDeque<>();
  private final AtomicInteger runs = new AtomicInteger();
  private final AtomicInteger running = new AtomicInteger();
  private final AtomicInteger maxConcurrentRuns = new AtomicInteger();
  private Runnable duringFirstRun = () -> {};
  private Throwable failure;

  void returns(Boolean... scripted) {
    results.addAll(Arrays.asList(scripted));
  }

  void duringFirstRun(Runnable hook) {
    duringFirstRun = hook;
  }

  void failWith(Throwable failure) {
    this.failure = failure;
  }

  int runs() {
    return runs.get();
  }

  int maxConcurrentRuns() {
    return maxConcurrentRuns.get();
  }

  @Override
  public boolean execute() {
    maxConcurrentRuns.accumulateAndGet(running.incrementAndGet(), Math::max);
    try {
      return run();
    } finally {
      running.decrementAndGet();
    }
  }

  private boolean run() {
    if (runs.incrementAndGet() == 1) {
      duringFirstRun.run();
    }
    if (failure instanceof RuntimeException e) {
      throw e;
    }
    if (failure instanceof Error e) {
      throw e;
    }
    Boolean next = results.poll();
    return next != null && next;
  }
}
