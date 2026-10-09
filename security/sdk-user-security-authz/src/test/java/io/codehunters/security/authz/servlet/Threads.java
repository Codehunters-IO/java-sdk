package io.codehunters.security.authz.servlet;

import java.time.Duration;
import java.time.Instant;

/** Starts a request on its own thread and waits until it waits for the filter's first build. */
final class Threads {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private Threads() {}

  static Thread start(Runnable task) {
    Thread thread = new Thread(task, "waiting-request");
    thread.start();
    return thread;
  }

  static void awaitBlocked(Thread thread) throws InterruptedException {
    Instant deadline = Instant.now().plus(TIMEOUT);
    while (!isWaiting(thread) && Instant.now().isBefore(deadline)) {
      Thread.sleep(5);
    }
  }

  private static boolean isWaiting(Thread thread) {
    Thread.State state = thread.getState();
    return state == Thread.State.BLOCKED
        || state == Thread.State.WAITING
        || state == Thread.State.TIMED_WAITING;
  }
}
