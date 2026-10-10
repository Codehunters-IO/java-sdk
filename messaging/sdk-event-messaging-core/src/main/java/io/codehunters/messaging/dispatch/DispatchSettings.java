package io.codehunters.messaging.dispatch;

import io.codehunters.messaging.ClaimRequest;
import java.time.Clock;
import java.util.Objects;

/**
 * Settings of {@link SendOutboxMessagesUseCase}, grouped so its constructor stays at three
 * parameters.
 *
 * @param claim what each run claims; publishing stops once half of its TTL has elapsed
 * @param retry the retry policy of failed publishes
 * @param clock the clock of every timestamp the use case writes
 */
public record DispatchSettings(ClaimRequest claim, RetryPolicy retry, Clock clock) {

  /** Validates the settings. */
  public DispatchSettings {
    Objects.requireNonNull(claim, "claim");
    Objects.requireNonNull(retry, "retry");
    Objects.requireNonNull(clock, "clock");
  }
}
