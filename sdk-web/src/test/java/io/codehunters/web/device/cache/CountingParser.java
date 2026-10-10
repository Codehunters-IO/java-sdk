package io.codehunters.web.device.cache;

import io.codehunters.web.device.useragent.UserAgentInfo;
import io.codehunters.web.device.useragent.UserAgentParser;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test fake: echoes the header as the browser family and counts calls; can block until released and
 * can fail with an {@link Error} or a {@link RuntimeException} once released. Blocking may be
 * limited to one header.
 */
final class CountingParser implements UserAgentParser {

  private final AtomicInteger calls = new AtomicInteger();
  private final CountDownLatch entered = new CountDownLatch(1);
  private final CountDownLatch release;
  private final Throwable failure;
  private final String blockedHeader;

  private CountingParser(CountDownLatch release, Throwable failure, String blockedHeader) {
    this.release = release;
    this.failure = failure;
    this.blockedHeader = blockedHeader;
  }

  static CountingParser immediate() {
    return new CountingParser(new CountDownLatch(0), null, null);
  }

  static CountingParser blockingUntilReleased() {
    return new CountingParser(new CountDownLatch(1), null, null);
  }

  static CountingParser blockingOnlyOn(String header) {
    return new CountingParser(new CountDownLatch(1), null, header);
  }

  static CountingParser blockingThenFailing(Error failure) {
    return new CountingParser(new CountDownLatch(1), failure, null);
  }

  static CountingParser blockingThenFailing(RuntimeException failure) {
    return new CountingParser(new CountDownLatch(1), failure, null);
  }

  @Override
  public UserAgentInfo parse(String userAgent) {
    calls.incrementAndGet();
    if (blockedHeader == null || blockedHeader.equals(userAgent)) {
      entered.countDown();
      await(release);
    }
    if (failure instanceof Error error) {
      throw error;
    }
    if (failure instanceof RuntimeException exception) {
      throw exception;
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
