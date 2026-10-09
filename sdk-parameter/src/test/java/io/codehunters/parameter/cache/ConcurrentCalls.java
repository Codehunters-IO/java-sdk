package io.codehunters.parameter.cache;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.codehunters.parameter.Parameter;
import io.codehunters.parameter.ParameterClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Test fixture that runs one call while the delegate is blocked and a second call for the same key
 * that arrives during that load, then releases the delegate and collects both outcomes.
 */
final class ConcurrentCalls {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final Set<Thread.State> PARKED_OR_DONE =
      Set.of(Thread.State.WAITING, Thread.State.TIMED_WAITING, Thread.State.TERMINATED);

  private final ParameterClient delegate;
  private final CountDownLatch entered = new CountDownLatch(1);
  private final CountDownLatch release = new CountDownLatch(1);
  private final List<Object> results = Collections.synchronizedList(new ArrayList<>());
  private final List<RuntimeException> failures = Collections.synchronizedList(new ArrayList<>());

  ConcurrentCalls(ParameterClient delegate) {
    this.delegate = delegate;
  }

  void blockLookupWith(Optional<Parameter> answer) {
    when(delegate.lookup(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              blockUntilReleased();
              return answer;
            });
  }

  void failLookupWith(RuntimeException failure) {
    when(delegate.lookup(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              blockUntilReleased();
              throw failure;
            })
        .thenReturn(Optional.empty());
  }

  void startLoaderAndWaiter(Supplier<?> call) throws InterruptedException {
    Thread loader = Thread.ofPlatform().start(() -> record(call));
    entered.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    Thread waiter = Thread.ofPlatform().start(() -> record(call));
    await().atMost(TIMEOUT).until(() -> PARKED_OR_DONE.contains(waiter.getState()));
    release.countDown();
    loader.join(TIMEOUT);
    waiter.join(TIMEOUT);
  }

  List<Object> results() {
    return List.copyOf(results);
  }

  List<RuntimeException> failures() {
    return List.copyOf(failures);
  }

  private void blockUntilReleased() throws InterruptedException {
    entered.countDown();
    release.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
  }

  private void record(Supplier<?> call) {
    try {
      results.add(call.get());
    } catch (RuntimeException ex) {
      failures.add(ex);
    }
  }
}
