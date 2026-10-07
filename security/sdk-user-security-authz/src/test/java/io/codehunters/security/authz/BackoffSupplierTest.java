package io.codehunters.security.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BackoffSupplierTest {

  private static final Duration BACKOFF = Duration.ofSeconds(30);

  private final MutableClock clock = new MutableClock();
  private final AtomicInteger calls = new AtomicInteger();

  @Test
  void should_create_once_when_creation_succeeds() {
    var supplier = new BackoffSupplier<>(() -> "client-" + calls.incrementAndGet(), BACKOFF, clock);

    supplier.get();

    assertThat(supplier.get()).isEqualTo("client-1");
  }

  @Test
  void should_fail_fast_without_retrying_while_backing_off() {
    var supplier = new BackoffSupplier<String>(this::failing, BACKOFF, clock);
    assertThatThrownBy(supplier::get).isInstanceOf(IllegalStateException.class);

    clock.advance(BACKOFF.minusSeconds(1));

    assertThatThrownBy(supplier::get).isInstanceOf(IllegalStateException.class);
    assertThat(calls).hasValue(1);
  }

  @Test
  void should_retry_when_backoff_elapsed() {
    var supplier = new BackoffSupplier<String>(this::failing, BACKOFF, clock);
    assertThatThrownBy(supplier::get).isInstanceOf(IllegalStateException.class);

    clock.advance(BACKOFF);

    assertThatThrownBy(supplier::get).isInstanceOf(IllegalStateException.class);
    assertThat(calls).hasValue(2);
  }

  private String failing() {
    calls.incrementAndGet();
    throw new IllegalStateException("Keycloak unreachable");
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-05T12:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
