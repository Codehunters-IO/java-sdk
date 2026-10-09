package io.codehunters.messaging;

/**
 * Raised by a {@link MessagePublisherPort} adapter when the broker does not confirm a publish
 * (failure, timeout or interruption). The message is fixed and never copies the cause's message,
 * because broker errors may echo payload text; the cause is kept for its class only.
 */
public class MessagePublishException extends RuntimeException {

  private static final String MESSAGE = "Broker publish failed";

  /**
   * Wraps the broker failure.
   *
   * @param cause the underlying failure
   */
  public MessagePublishException(Throwable cause) {
    super(MESSAGE, cause);
  }
}
