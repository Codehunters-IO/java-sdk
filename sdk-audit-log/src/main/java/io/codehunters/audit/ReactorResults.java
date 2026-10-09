package io.codehunters.audit;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Reactor support for {@link AsyncResults}; loaded only when Reactor is on the classpath. */
final class ReactorResults {

  private static final boolean SECURITY_PRESENT =
      ClassUtils.isPresent(
          "org.springframework.security.core.context.ReactiveSecurityContextHolder",
          ReactorResults.class.getClassLoader());

  private ReactorResults() {}

  static Optional<Object> observe(
      Object result, Class<?> declaredType, AsyncResults.Completion completion) {
    if (result instanceof Mono<?> mono && declaredType.isAssignableFrom(Mono.class)) {
      return Optional.of(subscriberActor().flatMap(actor -> observed(mono, completion, actor)));
    }
    if (result instanceof Flux<?> flux && declaredType.isAssignableFrom(Flux.class)) {
      return Optional.of(subscriberActor().flatMapMany(actor -> observed(flux, completion, actor)));
    }
    return Optional.empty();
  }

  static AsyncResults.Completion offBlockingThreads(AsyncResults.Completion completion) {
    return (value, failure, start, actor) -> {
      if (!Schedulers.isInNonBlockingThread()) {
        completion.completed(value, failure, start, actor);
        return;
      }
      try {
        Schedulers.boundedElastic()
            .schedule(() -> completion.completed(value, failure, start, actor));
      } catch (RejectedExecutionException ex) {
        completion.completed(value, failure, start, actor);
      }
    };
  }

  private static Mono<Optional<String>> subscriberActor() {
    return SECURITY_PRESENT ? ReactorSecurityActor.current() : Mono.just(Optional.empty());
  }

  private static <T> Mono<T> observed(
      Mono<T> mono, AsyncResults.Completion completion, Optional<String> actor) {
    Once once = new Once(completion, System.nanoTime(), actor.orElse(null));
    return mono.doOnSuccess(once::succeeded).doOnError(once::failed).doOnCancel(once::cancelled);
  }

  private static <T> Flux<T> observed(
      Flux<T> flux, AsyncResults.Completion completion, Optional<String> actor) {
    Once once = new Once(completion, System.nanoTime(), actor.orElse(null));
    return flux.doOnComplete(() -> once.succeeded(null))
        .doOnError(once::failed)
        .doOnCancel(once::cancelled);
  }

  /** Reports the first terminal signal of one subscription and ignores the rest. */
  private static final class Once {

    private final AsyncResults.Completion completion;
    private final long start;
    private final String actor;
    private final AtomicBoolean reported = new AtomicBoolean();

    Once(AsyncResults.Completion completion, long start, String actor) {
      this.completion = completion;
      this.start = start;
      this.actor = actor;
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
        completion.completed(value, failure, start, actor);
      }
    }
  }
}
