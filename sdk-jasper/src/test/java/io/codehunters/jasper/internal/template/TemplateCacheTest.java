package io.codehunters.jasper.internal.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.codehunters.jasper.ReportException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TemplateCacheTest {

  @Test
  void should_load_a_template_once_when_it_repeats() {
    var loader = CountingLoader.immediate();
    var cache = new TemplateCache<>(2, loader);

    cache.get("a");
    var second = cache.get("a");

    assertThat(second).isEqualTo("loaded:a");
    assertThat(loader.calls()).isEqualTo(1);
  }

  @Test
  void should_keep_at_most_the_configured_number_of_templates() {
    var cache = new TemplateCache<>(2, CountingLoader.immediate());

    cache.get("a");
    cache.get("b");
    cache.get("c");

    assertThat(cache.size()).isEqualTo(2);
  }

  @Test
  void should_report_a_cache_of_size_zero_as_disabled() {
    assertThat(new TemplateCache<>(0, CountingLoader.immediate()).isEnabled()).isFalse();
  }

  @Test
  void should_report_a_sized_cache_as_enabled() {
    assertThat(new TemplateCache<>(1, CountingLoader.immediate()).isEnabled()).isTrue();
  }

  @Test
  void should_load_every_time_when_the_cache_size_is_zero() {
    var loader = CountingLoader.immediate();
    var cache = new TemplateCache<>(0, loader);

    cache.get("a");
    cache.get("a");

    assertThat(loader.calls()).isEqualTo(2);
  }

  @Test
  void should_not_cache_a_failure_so_the_next_call_loads_again() {
    var loader = CountingLoader.failingOnce(new ReportException("template a was not found"));
    var cache = new TemplateCache<>(2, loader);

    assertThatThrownBy(() -> cache.get("a")).hasMessage("template a was not found");
    assertThat(cache.get("a")).isEqualTo("loaded:a");
  }

  // The second caller is released only once it waits on the shared load (WAITING); a caller that
  // entered the loader instead would sit in its timed latch (TIMED_WAITING).
  @Test
  void should_share_one_load_between_concurrent_callers_of_the_same_template() {
    var loader = CountingLoader.blockingUntilReleased();
    var cache = new TemplateCache<>(2, loader);
    var first = CompletableFuture.supplyAsync(() -> cache.get("a"));
    loader.awaitEntered();
    var second = Waiter.start(() -> cache.get("a"));
    await().atMost(Duration.ofSeconds(5)).until(second::isWaiting);

    loader.release();

    assertThat(first.join())
        .isEqualTo(assertTimeoutPreemptively(Duration.ofSeconds(5), second.result()::join));
    assertThat(loader.calls()).isEqualTo(1);
  }

  @Test
  void should_hand_waiting_callers_the_failure_of_the_shared_load() {
    var loader = CountingLoader.blockingThenFailing(new ReportException("template a broke"));
    var cache = new TemplateCache<>(2, loader);
    var first = CompletableFuture.supplyAsync(() -> cache.get("a"));
    loader.awaitEntered();
    var second = Waiter.start(() -> cache.get("a"));
    await().atMost(Duration.ofSeconds(5)).until(second::isWaiting);

    loader.release();

    assertThatThrownBy(
            () -> assertTimeoutPreemptively(Duration.ofSeconds(5), second.result()::join))
        .hasRootCauseMessage("template a broke");
    assertThatThrownBy(first::join).hasRootCauseMessage("template a broke");
  }

  @Test
  void
      should_rethrow_the_same_raw_error_to_the_loader_and_its_waiters_when_the_loader_throws_one() {
    var loader = CountingLoader.blockingThenFailing(new StackOverflowError());
    var cache = new TemplateCache<>(2, loader);
    var first = CompletableFuture.supplyAsync(() -> cache.get("a"));
    loader.awaitEntered();
    var second = Waiter.start(() -> cache.get("a"));
    await().atMost(Duration.ofSeconds(5)).until(second::isWaiting);

    loader.release();

    assertThatThrownBy(
            () -> assertTimeoutPreemptively(Duration.ofSeconds(5), second.result()::join))
        .hasCauseInstanceOf(StackOverflowError.class);
    assertThatThrownBy(first::join).hasCauseInstanceOf(StackOverflowError.class);
  }

  @Test
  void should_fail_fast_instead_of_deadlocking_when_the_loader_reads_its_own_template() {
    var cache = new AtomicReference<TemplateCache<String>>();
    cache.set(new TemplateCache<>(2, name -> cache.get().get(name)));

    assertThatThrownBy(
            () -> assertTimeoutPreemptively(Duration.ofSeconds(5), () -> cache.get().get("a")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "re-entrant load of template 'a': a template loader must not read its own template");
  }

  @Test
  void should_let_the_loader_read_another_template_when_the_cache_is_shared() {
    var cache = new AtomicReference<TemplateCache<String>>();
    cache.set(new TemplateCache<>(2, name -> name.equals("main") ? cache.get().get("sub") : name));

    assertThat(cache.get().get("main")).isEqualTo("sub");
  }

  @Test
  void should_fail_without_caching_when_the_loader_returns_no_template() {
    var calls = new AtomicInteger();
    var cache = new TemplateCache<String>(2, name -> calls.incrementAndGet() == 1 ? null : name);

    assertThatThrownBy(() -> cache.get("a"))
        .isInstanceOf(ReportException.class)
        .hasMessage("the template loader returned no template for 'a'");
    assertThat(cache.get("a")).isEqualTo("a");
  }

  @Test
  void should_reject_a_negative_cache_size() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new TemplateCache<>(-1, CountingLoader.immediate()))
        .withMessage("cache size must not be negative");
  }
}
