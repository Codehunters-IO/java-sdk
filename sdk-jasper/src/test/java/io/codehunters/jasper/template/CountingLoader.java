package io.codehunters.jasper.template;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** A template loader that counts its calls and can block or fail on demand. */
final class CountingLoader implements Function<String, String> {

  private final AtomicInteger calls = new AtomicInteger();
  private final CountDownLatch entered = new CountDownLatch(1);
  private final CountDownLatch released;
  private final Throwable failure;
  private final boolean failOnlyOnce;

  private CountingLoader(boolean blocking, Throwable failure, boolean failOnlyOnce) {
    this.released = new CountDownLatch(blocking ? 1 : 0);
    this.failure = failure;
    this.failOnlyOnce = failOnlyOnce;
  }

  static CountingLoader immediate() {
    return new CountingLoader(false, null, false);
  }

  static CountingLoader blockingUntilReleased() {
    return new CountingLoader(true, null, false);
  }

  static CountingLoader blockingThenFailing(Throwable failure) {
    return new CountingLoader(true, failure, false);
  }

  static CountingLoader failingOnce(Throwable failure) {
    return new CountingLoader(false, failure, true);
  }

  @Override
  public String apply(String name) {
    int call = calls.incrementAndGet();
    entered.countDown();
    awaitRelease();
    if (failure != null && (!failOnlyOnce || call == 1)) {
      throwUnchecked(failure);
    }
    return "loaded:" + name;
  }

  int calls() {
    return calls.get();
  }

  void awaitEntered() {
    await(entered, "the loader was never entered");
  }

  void release() {
    released.countDown();
  }

  private void awaitRelease() {
    await(released, "the loader was never released");
  }

  private static void await(CountDownLatch latch, String message) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException(message);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(message, e);
    }
  }

  private static void throwUnchecked(Throwable failure) {
    if (failure instanceof Error error) {
      throw error;
    }
    throw (RuntimeException) failure;
  }
}
