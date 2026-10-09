package io.codehunters.measure.internal;

/** The meters of one measured call, recorded once the call's work finishes. */
@FunctionalInterface
interface Measurement {

  /** A measurement that records nothing, for a call whose meters could not be started. */
  Measurement NONE = (result, failure) -> {};

  /**
   * Records the outcome. Never throws.
   *
   * @param result the value the work produced, or {@code null}
   * @param failure the failure, or {@code null} on success
   */
  void finished(Object result, Throwable failure);
}
