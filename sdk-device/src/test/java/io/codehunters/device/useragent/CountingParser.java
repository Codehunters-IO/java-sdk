package io.codehunters.device.useragent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Test fake: echoes the header as the browser family and counts calls; can block until released. */
final class CountingParser implements UserAgentParser {

  private final AtomicInteger calls = new AtomicInteger();
  private final CountDownLatch entered = new CountDownLatch(1);
  private final CountDownLatch release;

  private CountingParser(CountDownLatch release) {
    this.release = release;
  }

  static CountingParser immediate() {
    return new CountingParser(new CountDownLatch(0));
  }

  static CountingParser blockingUntilReleased() {
    return new CountingParser(new CountDownLatch(1));
  }

  @Override
  public UserAgentInfo parse(String userAgent) {
    calls.incrementAndGet();
    entered.countDown();
    await(release);
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
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
