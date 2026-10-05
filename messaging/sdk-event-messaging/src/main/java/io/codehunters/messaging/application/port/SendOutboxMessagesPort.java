package io.codehunters.messaging.application.port;

/** Inbound port that triggers a dispatch of pending outbox messages. */
@FunctionalInterface
public interface SendOutboxMessagesPort {

  /** Dispatches one batch of pending outbox messages. */
  void execute();
}
