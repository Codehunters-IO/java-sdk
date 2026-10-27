package io.codehunters.messaging.kafka.consumer;

/**
 * Raised by {@link EnvelopeReader} when a record does not hold a readable event envelope: malformed
 * JSON, a value that does not bind to the envelope, or a missing {@code header} or {@code body}.
 * Retrying cannot fix it, so consumers treat it as non-retryable.
 *
 * <p>The message is fixed and there is no cause, because the parser message can echo input that may
 * carry personal data; only the parser exception's class name is kept, in {@link #causeClass()}.
 */
public class UnreadableEnvelopeException extends RuntimeException {

  /** {@link #causeClass()} when the envelope parsed but has no header or no body. */
  public static final String MISSING_HEADER_OR_BODY = "missing-header-or-body";

  private static final String MESSAGE = "Unreadable event envelope";

  /** The parser exception class name, or {@link #MISSING_HEADER_OR_BODY}. */
  private final String causeClass;

  /**
   * Creates the exception.
   *
   * @param causeClass the parser exception class name, or {@link #MISSING_HEADER_OR_BODY}
   */
  public UnreadableEnvelopeException(String causeClass) {
    super(MESSAGE);
    this.causeClass = causeClass;
  }

  /**
   * The reason the envelope could not be read, without any of its content.
   *
   * @return the parser exception class name, or {@link #MISSING_HEADER_OR_BODY}
   */
  public String causeClass() {
    return causeClass;
  }
}
