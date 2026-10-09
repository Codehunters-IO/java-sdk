package io.codehunters.audit.internal.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Reactor results are audited, with no subscriber actor, when Spring Security is absent. */
class ReactorResultsWithoutSecurityTest {

  private static final String NO_ACTOR = "no actor";

  private final ReactorHidingClassLoader loader =
      new ReactorHidingClassLoader("org.springframework.security.");
  private final AtomicReference<Object> completedWith = new AtomicReference<>();
  private final AtomicReference<Object> actor = new AtomicReference<>(NO_ACTOR);

  @AfterEach
  void closeLoader() throws Exception {
    loader.close();
  }

  @Test
  void should_report_the_value_and_no_actor_without_spring_security() throws Exception {
    Object mono =
        loader
            .loadClass("reactor.core.publisher.Mono")
            .getMethod("just", Object.class)
            .invoke(null, "done");

    Object observed = observe(mono).orElseThrow();
    observed.getClass().getMethod("block").invoke(observed);

    assertThat(completedWith).hasValue("done");
    assertThat(actor).hasValue(null);
  }

  @SuppressWarnings("unchecked")
  private Optional<Object> observe(Object result) throws Exception {
    Class<?> asyncResults = loader.loadClass("io.codehunters.audit.internal.reactive.AsyncResults");
    Class<?> completion =
        loader.loadClass("io.codehunters.audit.internal.reactive.AsyncResults$Completion");
    Class<?> monoType = loader.loadClass("reactor.core.publisher.Mono");
    Method observe =
        asyncResults.getDeclaredMethod(
            "observe", Object.class, Class.class, long.class, completion);
    observe.setAccessible(true);
    return (Optional<Object>) observe.invoke(null, result, monoType, 0L, recording(completion));
  }

  private Object recording(Class<?> completion) {
    return Proxy.newProxyInstance(
        loader,
        new Class<?>[] {completion},
        (proxy, method, args) -> {
          completedWith.set(args[0]);
          actor.set(args[3]);
          return null;
        });
  }
}
