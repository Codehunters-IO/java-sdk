package io.codehunters.messaging.jdbc.scheduler;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

/**
 * Thread-safe {@link LockProvider} fake that records every lock request and either grants it or,
 * when built with {@link #held()}, reports the lock as held by another instance.
 */
public final class RecordingLockProvider implements LockProvider {

  private final boolean grants;
  private final List<LockConfiguration> requests = new CopyOnWriteArrayList<>();
  private final AtomicInteger unlocks = new AtomicInteger();
  private final CountDownLatch expected;

  private RecordingLockProvider(boolean grants, int expectedRequests) {
    this.grants = grants;
    this.expected = new CountDownLatch(expectedRequests);
  }

  /** Returns a provider that grants every lock. */
  public static RecordingLockProvider granting() {
    return new RecordingLockProvider(true, 1);
  }

  /**
   * Returns a provider that grants every lock and lets {@link #awaitRequests()} wait for the given
   * number of requests.
   */
  public static RecordingLockProvider granting(int expectedRequests) {
    return new RecordingLockProvider(true, expectedRequests);
  }

  /** Returns a provider whose locks are always held elsewhere. */
  public static RecordingLockProvider held() {
    return new RecordingLockProvider(false, 1);
  }

  @Override
  public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
    requests.add(lockConfiguration);
    expected.countDown();
    return grants ? Optional.of(unlocks::incrementAndGet) : Optional.empty();
  }

  /** Returns the lock requests received so far. */
  public List<LockConfiguration> requests() {
    return List.copyOf(requests);
  }

  /** Returns how many granted locks were released. */
  public int unlocks() {
    return unlocks.get();
  }

  /** Waits up to five seconds for the expected number of requests. */
  public boolean awaitRequests() throws InterruptedException {
    return expected.await(5, TimeUnit.SECONDS);
  }
}
