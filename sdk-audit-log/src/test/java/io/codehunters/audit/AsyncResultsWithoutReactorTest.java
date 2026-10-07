package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Async auditing works, and never touches Reactor, when Reactor is not on the classpath. */
class AsyncResultsWithoutReactorTest {

  private final ReactorHidingClassLoader loader = new ReactorHidingClassLoader();
  private final AtomicReference<Object> completedWith = new AtomicReference<>();

  @AfterEach
  void closeLoader() throws Exception {
    loader.close();
  }

  @Test
  void should_observe_a_future_without_reactor() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();

    Optional<Object> observed = observe(future, CompletableFuture.class);
    future.complete("done");

    assertThat(observed).containsSame(future);
    assertThat(completedWith).hasValue("done");
  }

  @Test
  void should_leave_a_plain_value_alone_without_reactor() throws Exception {
    assertThat(observe("plain", String.class)).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private Optional<Object> observe(Object result, Class<?> declaredType) throws Exception {
    Class<?> asyncResults = loader.loadClass("io.codehunters.audit.AsyncResults");
    Class<?> completion = loader.loadClass("io.codehunters.audit.AsyncResults$Completion");
    Method observe =
        asyncResults.getDeclaredMethod(
            "observe", Object.class, Class.class, long.class, completion);
    observe.setAccessible(true);
    return (Optional<Object>) observe.invoke(null, result, declaredType, 0L, recording(completion));
  }

  private Object recording(Class<?> completion) {
    return Proxy.newProxyInstance(
        loader,
        new Class<?>[] {completion},
        (proxy, method, args) -> {
          completedWith.set(args[0]);
          return null;
        });
  }
}
