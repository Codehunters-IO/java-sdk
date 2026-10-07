package io.codehunters.audit;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Reactor support for {@link AsyncResults}; loaded only when Reactor is on the classpath. */
final class ReactorResults {

  private ReactorResults() {}

  static Optional<Object> observe(
      Object result, Class<?> declaredType, AsyncResults.Completion completion) {
    if (result instanceof Mono<?> mono && declaredType.isAssignableFrom(Mono.class)) {
      return Optional.of(Mono.defer(() -> observed(mono, completion)));
    }
    if (result instanceof Flux<?> flux && declaredType.isAssignableFrom(Flux.class)) {
      return Optional.of(Flux.defer(() -> observed(flux, completion)));
    }
    return Optional.empty();
  }

  static AsyncResults.Completion offBlockingThreads(AsyncResults.Completion completion) {
    return (value, failure, start) -> {
      if (!Schedulers.isInNonBlockingThread()) {
        completion.completed(value, failure, start);
        return;
      }
      try {
        Schedulers.boundedElastic().schedule(() -> completion.completed(value, failure, start));
      } catch (RejectedExecutionException ex) {
        completion.completed(value, failure, start);
      }
    };
  }

  private static Mono<?> observed(Mono<?> mono, AsyncResults.Completion completion) {
    Once once = new Once(completion, System.nanoTime());
    return mono.doOnSuccess(once::succeeded).doOnError(once::failed).doOnCancel(once::cancelled);
  }

  private static Flux<?> observed(Flux<?> flux, AsyncResults.Completion completion) {
    Once once = new Once(completion, System.nanoTime());
    return flux.doOnComplete(() -> once.succeeded(null))
        .doOnError(once::failed)
        .doOnCancel(once::cancelled);
  }

  /** Reports the first terminal signal of one subscription and ignores the rest. */
  private static final class Once {

    private final AsyncResults.Completion completion;
    private final long start;
    private final AtomicBoolean reported = new AtomicBoolean();

    Once(AsyncResults.Completion completion, long start) {
      this.completion = completion;
      this.start = start;
    }

    void succeeded(Object value) {
      report(value, null);
    }

    void failed(Throwable failure) {
      report(null, failure);
    }

    void cancelled() {
      report(null, new CancellationException("subscription cancelled"));
    }

    private void report(Object value, Throwable failure) {
      if (reported.compareAndSet(false, true)) {
        completion.completed(value, failure, start);
      }
    }
  }
}
