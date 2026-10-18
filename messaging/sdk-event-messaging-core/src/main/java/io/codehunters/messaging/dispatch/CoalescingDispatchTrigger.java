package io.codehunters.messaging.dispatch;

import io.codehunters.messaging.OutboxDispatchTrigger;
import io.codehunters.messaging.SendOutboxMessagesPort;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link OutboxDispatchTrigger} that runs the dispatch on an {@link Executor}, at most one run at a
 * time per trigger. A request while idle submits one drain; requests while a drain runs collapse
 * into a single further run, so a burst of commits costs one or two dispatches, not one each. The
 * drain also keeps going while a run reports a full claim, so a backlog empties without waiting for
 * the poll.
 *
 * <p>The dispatch port is looked up on each run, which lets the store that requests dispatches be
 * created before the dispatch use case that depends on it. A run without a port does nothing. A
 * failed run is logged by exception class only, since messages may carry payload data; a rejected
 * submission, for example during shutdown, leaves the trigger idle for the next request.
 *
 * <p>{@link #stop()} ends the trigger until {@link #resume()}: later requests are dropped, and a
 * drain in progress finishes its current run but starts no further one. A drain whose thread is
 * interrupted, as an executor shutting down does, also stops after its current run and keeps the
 * interrupt flag.
 */
public final class CoalescingDispatchTrigger implements OutboxDispatchTrigger {

  private static final Logger log = LoggerFactory.getLogger(CoalescingDispatchTrigger.class);

  private static final int IDLE = 0;
  private static final int RUNNING = 1;
  private static final int RERUN = 2;

  private final AtomicInteger state = new AtomicInteger(IDLE);
  private final Executor executor;
  private final Supplier<SendOutboxMessagesPort> dispatch;
  private volatile boolean stopped;

  /**
   * Creates the trigger.
   *
   * @param executor runs the drains; never the requesting thread's work
   * @param dispatch supplies the dispatch port on each run, or {@code null} when there is none
   */
  public CoalescingDispatchTrigger(Executor executor, Supplier<SendOutboxMessagesPort> dispatch) {
    this.executor = Objects.requireNonNull(executor, "executor");
    this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
  }

  @Override
  public void requestDispatch() {
    if (stopped) {
      return;
    }
    int previous = state.getAndUpdate(current -> current == IDLE ? RUNNING : RERUN);
    if (previous == IDLE) {
      submit();
    }
  }

  /** Stops the trigger: drops later requests and lets a running drain end after its current run. */
  public void stop() {
    stopped = true;
  }

  /**
   * Undoes {@link #stop()}, for a context that is started again after a stop: later requests submit
   * drains as before. A request that races with a drain still ending its stop may be dropped; the
   * scheduled poll picks its rows up.
   */
  public void resume() {
    stopped = false;
  }

  private void submit() {
    try {
      executor.execute(this::drain);
    } catch (RejectedExecutionException e) {
      state.set(IDLE);
      log.debug("Outbox dispatch request dropped: {}", e.getClass().getName());
    }
  }

  // An Error escaping a run, a stop or an interrupt must not leave the trigger RUNNING forever.
  private void drain() {
    boolean finished = false;
    try {
      finished = drainUntilIdle();
    } finally {
      if (!finished) {
        state.set(IDLE);
      }
    }
  }

  // False when halted before the trigger went back to IDLE; true once tryFinish set it IDLE.
  private boolean drainUntilIdle() {
    do {
      runWhileFull();
      if (isHalted()) {
        return false;
      }
    } while (!tryFinish());
    return true;
  }

  private void runWhileFull() {
    boolean full = runOnce();
    while (full && !isHalted()) {
      full = runOnce();
    }
  }

  private boolean isHalted() {
    return stopped || Thread.currentThread().isInterrupted();
  }

  // Only the drain leaves RERUN, so when RUNNING -> IDLE fails a request came in during the run
  // and one more run is owed.
  private boolean tryFinish() {
    if (state.compareAndSet(RUNNING, IDLE)) {
      return true;
    }
    state.set(RUNNING);
    return false;
  }

  private boolean runOnce() {
    try {
      SendOutboxMessagesPort port = dispatch.get();
      return port != null && port.execute();
    } catch (RuntimeException e) {
      log.warn("Requested outbox dispatch failed: {}", e.getClass().getName());
      return false;
    }
  }
}
