package io.codehunters.messaging.domain.model;

/** String constants for the supported event-type values stamped on the event body. */
public final class EventTypes {

  /** Domain event (something happened). */
  public static final String EVENT = "EVENT";

  /** Command (intent to do something). */
  public static final String COMMAND = "COMMAND";

  private EventTypes() {}
}
