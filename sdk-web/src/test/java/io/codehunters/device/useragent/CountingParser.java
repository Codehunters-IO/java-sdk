package io.codehunters.device.useragent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test fake: echoes the header as the browser family and counts calls; can block until released and
 * can fail with an {@link Error} once released.
 */
final class CountingParser implements UserAgentParser {

  private final AtomicInteger calls = new AtomicInteger();
  private final CountDownLatch entered = new CountDownLatch(1);
  private final CountDownLatch release;
  private final Error failure;

  private CountingParser(CountDownLatch release, Error failure) {
    this.release = release;
    this.failure = failure;
  }

  static CountingParser immediate() {
    return new CountingParser(new CountDownLatch(0), null);
  }

  static CountingParser blockingUntilReleased() {
    return new CountingParser(new CountDownLatch(1), null);
  }

  static CountingParser blockingThenFailing(Error failure) {
    return new CountingParser(new CountDownLatch(1), failure);
  }

  @Override
  public UserAgentInfo parse(String userAgent) {
    calls.incrementAndGet();
    entered.countDown();
    await(release);
    if (failure != null) {
      throw failure;
    }
    return new UserAgentInfo(userAgent, "1", "Other", "", "Other");
  }

  int calls() {
    return calls.get();
  }

  void awaitEntered() {
    await(entered);
  }

  void release() {
    release.countDown();
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("latch was not released within 5 seconds");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
