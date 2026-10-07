package io.codehunters.device.useragent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CachingUserAgentParserTest {

  @Test
  void should_parse_a_header_once_when_it_repeats() {
    var delegate = CountingParser.immediate();
    var parser = new CachingUserAgentParser(delegate, 2);

    parser.parse("a");
    var second = parser.parse("a");

    assertThat(second.browserFamily()).isEqualTo("a");
    assertThat(delegate.calls()).isEqualTo(1);
  }

  @Test
  void should_evict_the_least_recently_used_header_when_full() {
    var delegate = CountingParser.immediate();
    var parser = new CachingUserAgentParser(delegate, 2);

    parser.parse("a");
    parser.parse("b");
    parser.parse("a");
    parser.parse("c");
    parser.parse("b");

    assertThat(delegate.calls()).isEqualTo(4);
  }

  @Test
  void should_parse_every_time_when_the_cache_size_is_zero() {
    var delegate = CountingParser.immediate();
    var parser = new CachingUserAgentParser(delegate, 0);

    parser.parse("a");
    parser.parse("a");

    assertThat(delegate.calls()).isEqualTo(2);
  }

  @Test
  void should_answer_unknown_when_the_delegate_fails() {
    var parser =
        new CachingUserAgentParser(
            header -> {
              throw new IllegalStateException("boom");
            },
            2);

    assertThat(parser.parse("a")).isEqualTo(UserAgentInfo.UNKNOWN);
  }

  @Test
  void should_answer_unknown_without_parsing_when_the_header_is_null() {
    var delegate = CountingParser.immediate();

    assertThat(new CachingUserAgentParser(delegate, 2).parse(null))
        .isEqualTo(UserAgentInfo.UNKNOWN);
    assertThat(delegate.calls()).isZero();
  }

  // The second caller is released only once it waits on the shared parse (WAITING); a caller
  // that entered the delegate instead would sit in its timed latch (TIMED_WAITING).
  @Test
  void should_share_one_parse_between_concurrent_callers_of_the_same_header() {
    var delegate = CountingParser.blockingUntilReleased();
    var parser = new CachingUserAgentParser(delegate, 2);
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
  void should_release_waiting_callers_with_unknown_when_the_delegate_throws_an_error() {
    var delegate = CountingParser.blockingThenFailing(new StackOverflowError());
    var parser = new CachingUserAgentParser(delegate, 2);
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
    var parser = new CachingUserAgentParser(new FailingOnceParser(new StackOverflowError()), 2);

    assertThatThrownBy(() -> parser.parse("a")).isInstanceOf(StackOverflowError.class);
    var again = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> parser.parse("a"));

    assertThat(again.browserFamily()).isEqualTo("a");
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
        .isThrownBy(() -> new CachingUserAgentParser(null, 2))
        .withMessage("delegate must not be null");
  }
}
