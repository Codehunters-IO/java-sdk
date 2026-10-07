package io.codehunters.audit;

import java.util.Optional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Reactor support for {@link AsyncResults}; loaded only when Reactor is on the classpath. */
final class ReactorResults {

  private ReactorResults() {}

  static Optional<Object> observe(Object result, AsyncResults.Completion completion) {
    if (result instanceof Mono<?> mono) {
      return Optional.of(Mono.defer(() -> observed(mono, completion)));
    }
    if (result instanceof Flux<?> flux) {
      return Optional.of(Flux.defer(() -> observed(flux, completion)));
    }
    return Optional.empty();
  }

  private static Mono<?> observed(Mono<?> mono, AsyncResults.Completion completion) {
    long start = System.nanoTime();
    return mono.doOnSuccess(value -> completion.completed(value, null, start))
        .doOnError(failure -> completion.completed(null, failure, start));
  }

  private static Flux<?> observed(Flux<?> flux, AsyncResults.Completion completion) {
    long start = System.nanoTime();
    return flux.doOnComplete(() -> completion.completed(null, null, start))
        .doOnError(failure -> completion.completed(null, failure, start));
  }
}
