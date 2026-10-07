package io.codehunters.audit;

import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.springframework.util.ClassUtils;

/**
 * Recognizes return values whose work finishes after the method returns, and reports their outcome
 * when it does. A {@link CompletionStage} is observed and returned unchanged, and its duration runs
 * from the method call. A Reactor {@code Mono} or {@code Flux} is returned decorated and reports
 * once per subscription, with its duration running from the subscription; cancellation is reported
 * as a {@link java.util.concurrent.CancellationException}. When Reactor is present, an outcome
 * signalled on a non-blocking thread is reported on Reactor's bounded elastic scheduler instead, so
 * a blocking write never stalls an event loop.
 */
final class AsyncResults {

  private static final boolean REACTOR_PRESENT =
      ClassUtils.isPresent("reactor.core.publisher.Mono", AsyncResults.class.getClassLoader());

  private AsyncResults() {}

  /**
   * Observes the result if its work is still running.
   *
   * @param result the value the method returned
   * @param declaredType the method's declared return type, which a decorated value must still fit
   * @param callStartNanos the {@link System#nanoTime()} at which the method was called
   * @param completion notified once the work finishes
   * @return the value to hand back to the caller, or empty if the work has already finished
   */
  static Optional<Object> observe(
      Object result, Class<?> declaredType, long callStartNanos, Completion completion) {
    Completion reported =
        REACTOR_PRESENT ? ReactorResults.offBlockingThreads(completion) : completion;
    if (result instanceof CompletionStage<?> stage) {
      stage.whenComplete(
          (value, failure) -> reported.completed(value, unwrap(failure), callStartNanos));
      return Optional.of(stage);
    }
    if (REACTOR_PRESENT) {
      return ReactorResults.observe(result, declaredType, reported);
    }
    return Optional.empty();
  }

  private static Throwable unwrap(Throwable failure) {
    if (failure instanceof CompletionException && failure.getCause() != null) {
      return failure.getCause();
    }
    return failure;
  }

  /** Receives the outcome of work that finished after the method returned. */
  @FunctionalInterface
  interface Completion {

    /**
     * Called once the work finishes.
     *
     * @param value the produced value, or {@code null}
     * @param failure the failure, or {@code null} on success
     * @param startNanos the {@link System#nanoTime()} from which the duration is measured
     */
    void completed(Object value, Throwable failure, long startNanos);
  }
}
