package io.codehunters.measure;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/** Runs tasks on separate threads released at the same instant, and collects their results. */
final class Concurrently {

  private Concurrently() {}

  static <T> List<T> run(int threads, IntFunction<T> task) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      List<Future<T>> futures =
          IntStream.range(0, threads)
              .mapToObj(i -> pool.submit(gated(start, () -> task.apply(i))))
              .toList();
      start.countDown();
      return futures.stream().map(Concurrently::join).toList();
    }
  }

  private static <T> Callable<T> gated(CountDownLatch start, Callable<T> task) {
    return () -> {
      start.await();
      return task.call();
    };
  }

  private static <T> T join(Future<T> future) {
    try {
      return future.get();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
