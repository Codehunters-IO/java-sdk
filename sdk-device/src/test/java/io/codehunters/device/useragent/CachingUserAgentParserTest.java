package io.codehunters.device.useragent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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

  @Test
  void should_share_one_parse_between_concurrent_callers_of_the_same_header() {
    var delegate = CountingParser.blockingUntilReleased();
    var parser = new CachingUserAgentParser(delegate, 2);
    var first = CompletableFuture.supplyAsync(() -> parser.parse("a"));
    delegate.awaitEntered();
    var second = CompletableFuture.supplyAsync(() -> parser.parse("a"));

    delegate.release();

    assertThat(first.join()).isEqualTo(second.join());
    assertThat(delegate.calls()).isEqualTo(1);
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
