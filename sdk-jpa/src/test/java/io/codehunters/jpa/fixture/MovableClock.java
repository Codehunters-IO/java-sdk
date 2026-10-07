package io.codehunters.jpa.fixture;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** A clock tests move by hand. */
public final class MovableClock extends Clock {

  private final AtomicReference<Instant> now;

  public MovableClock(Instant start) {
    this.now = new AtomicReference<>(start);
  }

  public void moveTo(Instant instant) {
    now.set(instant);
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return now.get();
  }
}
