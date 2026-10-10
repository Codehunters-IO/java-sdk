package io.codehunters.web.device.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.codehunters.web.device.useragent.UserAgentInfo;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CachingUserAgentParserTest {

  private static final int MAX_ENTRIES = 2;

  @Test
  void should_parse_a_header_once_when_it_repeats() {
    var delegate = CountingParser.immediate();
    var parser = new CachingUserAgentParser(delegate, MAX_ENTRIES);

    parser.parse("a");
    var second = parser.parse("a");

    assertThat(second.browserFamily()).isEqualTo("a");
    assertThat(delegate.calls()).isEqualTo(1);
  }

  @Test
  void should_keep_at_most_the_configured_number_of_headers_when_many_differ() {
    var parser = new CachingUserAgentParser(CountingParser.immediate(), MAX_ENTRIES);

    IntStream.range(0, 50).mapToObj(Integer::toString).forEach(parser::parse);
    var cache = parser.cache().orElseThrow();
    cache.cleanUp();

    assertThat(cache.estimatedSize()).isLessThanOrEqualTo(MAX_ENTRIES);
  }

  @Test
  void should_record_hits_and_misses_when_headers_repeat() {
    var parser = new CachingUserAgentParser(CountingParser.immediate(), MAX_ENTRIES);

    parser.parse("a");
    parser.parse("a");
    var stats = parser.cache().orElseThrow().stats();

    assertThat(stats.missCount()).isEqualTo(1);
    assertThat(stats.hitCount()).isEqualTo(1);
  }

  @Test
  void should_parse_every_time_without_a_cache_when_the_cache_size_is_zero() {
    var delegate = CountingParser.immediate();
    var parser = new CachingUserAgentParser(delegate, 0);

    parser.parse("a");
    parser.parse("a");

    assertThat(delegate.calls()).isEqualTo(2);
    assertThat(parser.cache()).isEmpty();
  }

  @Test
  void should_answer_unknown_when_the_delegate_fails() {
    var parser =
        new CachingUserAgentParser(
            header -> {
              throw new IllegalStateException("boom");
            },
            MAX_ENTRIES);

    assertThat(parser.parse("a")).isEqualTo(UserAgentInfo.UNKNOWN);
  }

  @Test
  void should_answer_unknown_without_parsing_when_the_header_is_null() {
    var delegate = CountingParser.immediate();

    assertThat(new CachingUserAgentParser(delegate, MAX_ENTRIES).parse(null))
        .isEqualTo(UserAgentInfo.UNKNOWN);
    assertThat(delegate.calls()).isZero();
  }

  // The second caller is released only once it waits on the shared parse (WAITING); a caller
  // that entered the delegate instead would sit in its timed latch (TIMED_WAITING).
  @Test
  void should_share_one_parse_between_concurrent_callers_of_the_same_header() {
    var delegate = CountingParser.blockingUntilReleased();
    var parser = new CachingUserAgentParser(delegate, MAX_ENTRIES);
    var first = CompletableFuture.supplyAsync(() -> parser.parse("a"));
    delegate.awaitEntered();
    var second = new CompletableFuture<UserAgentInfo>();
    var waiter = Thread.ofPlatform().start(() -> second.complete(parser.parse("a")));
    await().atMost(Duration.ofSeconds(5)).until(() -> waiter.getState() == Thread.State.WAITING);

    delegate.release();

    assertThat(first.join())
        .isEqualTo(assertTimeoutPreemptively(Duration.ofSeconds(5), second::join));
    assertThat(delegate.calls()).isEqualTo(1);
  }

  @Test
  void should_not_block_other_headers_while_one_is_being_parsed() {
    var delegate = CountingParser.blockingOnlyOn("slow");
    var parser = new CachingUserAgentParser(delegate, MAX_ENTRIES);
    var first = CompletableFuture.supplyAsync(() -> parser.parse("slow"));
    delegate.awaitEntered();

    var other = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> parser.parse("fast"));

    delegate.release();
    assertThat(other.browserFamily()).isEqualTo("fast");
    assertThat(first.join().browserFamily()).isEqualTo("slow");
  }

  @Test
  void should_release_waiting_callers_with_unknown_when_the_delegate_throws_an_error() {
    var delegate = CountingParser.blockingThenFailing(new StackOverflowError());
    var parser = new CachingUserAgentParser(delegate, MAX_ENTRIES);
    var first = CompletableFuture.supplyAsync(() -> parser.parse("a"));
    delegate.awaitEntered();
    var second = new CompletableFuture<UserAgentInfo>();
    var waiter = Thread.ofPlatform().start(() -> second.complete(parser.parse("a")));
    await().atMost(Duration.ofSeconds(5)).until(() -> waiter.getState() == Thread.State.WAITING);

    delegate.release();

    assertThat(assertTimeoutPreemptively(Duration.ofSeconds(5), second::join))
        .isEqualTo(UserAgentInfo.UNKNOWN);
    assertThatThrownBy(first::join).hasCauseInstanceOf(StackOverflowError.class);
  }

  @Test
  void should_parse_the_header_again_after_the_delegate_threw_an_error() {
    var parser =
        new CachingUserAgentParser(new FailingOnceParser(new StackOverflowError()), MAX_ENTRIES);

    assertThatThrownBy(() -> parser.parse("a")).isInstanceOf(StackOverflowError.class);
    var again = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> parser.parse("a"));

    assertThat(again.browserFamily()).isEqualTo("a");
  }

  @Test
  void should_parse_the_header_again_when_the_delegate_threw_a_runtime_exception_before() {
    var delegate = new FailingOnceParser(new IllegalStateException("boom"));
    var parser = new CachingUserAgentParser(delegate, MAX_ENTRIES);

    var failed = parser.parse("a");
    var again = parser.parse("a");

    assertThat(failed).isEqualTo(UserAgentInfo.UNKNOWN);
    assertThat(again.browserFamily()).isEqualTo("a");
  }

  @Test
  void should_release_waiting_callers_with_unknown_when_the_delegate_throws_a_runtime_exception() {
    var delegate = CountingParser.blockingThenFailing(new IllegalStateException("boom"));
    var parser = new CachingUserAgentParser(delegate, MAX_ENTRIES);
    var first = CompletableFuture.supplyAsync(() -> parser.parse("a"));
    delegate.awaitEntered();
    var second = new CompletableFuture<UserAgentInfo>();
    var waiter = Thread.ofPlatform().start(() -> second.complete(parser.parse("a")));
    await().atMost(Duration.ofSeconds(5)).until(() -> waiter.getState() == Thread.State.WAITING);

    delegate.release();

    assertThat(assertTimeoutPreemptively(Duration.ofSeconds(5), second::join))
        .isEqualTo(UserAgentInfo.UNKNOWN);
    assertThat(first.join()).isEqualTo(UserAgentInfo.UNKNOWN);
    assertThat(parser.cache().orElseThrow().getIfPresent("a")).isNull();
  }

  @Test
  void should_reject_a_negative_cache_size() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CachingUserAgentParser(CountingParser.immediate(), -1))
        .withMessage("cache size must not be negative");
  }

  @Test
  void should_reject_a_null_delegate() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CachingUserAgentParser(null, MAX_ENTRIES))
        .withMessage("delegate must not be null");
  }
}
