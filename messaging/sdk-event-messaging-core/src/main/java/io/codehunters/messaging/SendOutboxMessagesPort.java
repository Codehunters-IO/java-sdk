package io.codehunters.messaging;

/** Inbound port that triggers a dispatch of pending outbox messages. */
@FunctionalInterface
public interface SendOutboxMessagesPort {

  /**
   * Dispatches one batch of pending outbox messages.
   *
   * @return {@code true} when the claim came back full, so more messages may be waiting
   */
  boolean execute();
}
