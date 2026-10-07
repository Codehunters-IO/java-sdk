package io.codehunters.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock that cannot tell the time, so an audit event cannot be built. */
final class BrokenClock extends Clock {

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
    throw new IllegalStateException("clock unavailable");
  }
}
