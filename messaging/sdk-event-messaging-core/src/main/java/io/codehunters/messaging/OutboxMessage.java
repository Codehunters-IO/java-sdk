package io.codehunters.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent outbox row: a message awaiting publication to the broker, with retry bookkeeping and a
 * claim-based dispatch lifecycle. Immutable: every state change returns a new message.
 *
 * @param id the row id, sent to the broker as {@link MessagePublisherPort#OUTBOX_ID_HEADER}
 * @param seq database-assigned insertion order, which per-key ordering uses; never inserted, {@code
 *     null} until the row is read back
 * @param origin the application that wrote the row
 * @param routingKey the AMQP routing key, or null
 * @param topic the destination topic or exchange
 * @param key the per-key ordering key ({@code message_key}), or null for an unordered row
 * @param message the body: the event before it is saved, its JSON once claimed
 * @param status the lifecycle status; {@link OutboxStatus#PENDING} when null
 * @param attempts the publish attempts charged so far
 * @param error the class name of the last publish failure, or null
 * @param occurredAt when the row was created
 * @param nextAttemptAt the earliest time the row may be claimed again, or null
 * @param publishedAt when the broker confirmed the row, or null
 * @param claimedAt when the current claim was taken, or null
 * @param claimedBy the claim marker that fences updates, or null
 * @param sendStartedAt when the claim owner started handing the row to the broker, or null; a
 *     reclaim charges an attempt only when it is set
 * @param traceparent W3C {@code traceparent} of the work that created the message, sent as the
 *     {@code traceparent} broker header; null when no trace was active
 * @param <T> the message body type
 */
public record OutboxMessage<T>(
    UUID id,
    Long seq,
    String origin,
    String routingKey,
    String topic,
    String key,
    T message,
    OutboxStatus status,
    int attempts,
    String error,
    Instant occurredAt,
    Instant nextAttemptAt,
    Instant publishedAt,
    Instant claimedAt,
    String claimedBy,
    Instant sendStartedAt,
    String traceparent) {

  /** Defaults a missing status to {@link OutboxStatus#PENDING}. */
  public OutboxMessage {
    status = status == null ? OutboxStatus.PENDING : status;
  }

  /**
   * Starts a builder for a new message.
   *
   * @param <T> the message body type
   * @return an empty builder, with status {@link OutboxStatus#PENDING} and no attempts
   */
  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  /**
   * A builder initialised from this message.
   *
   * @return the builder
   */
  public Builder<T> toBuilder() {
    return new Builder<T>().copyOf(this);
  }

  /**
   * The message as its owner started handing it to the broker.
   *
   * @param at when the send started
   * @return the new message
   */
  public OutboxMessage<T> sendStarted(Instant at) {
    return toBuilder().sendStartedAt(at).build();
  }

  /**
   * The message as confirmed by the broker: {@link OutboxStatus#PUBLISHED}, without error or next
   * attempt.
   *
   * @param at when the broker confirmed it
   * @return the new message
   */
  public OutboxMessage<T> published(Instant at) {
    return toBuilder()
        .status(OutboxStatus.PUBLISHED)
        .publishedAt(at)
        .error(null)
        .nextAttemptAt(null)
        .build();
  }

  /**
   * The message after a failed publish: one more attempt charged and the failure recorded. The
   * status is unchanged; follow with {@link #retryAt} or {@link #failed}.
   *
   * @param error the failure's class name, never its message
   * @return the new message
   */
  public OutboxMessage<T> attemptFailed(String error) {
    return toBuilder().attempts(attempts + 1).error(error).build();
  }

  /**
   * The message waiting for another attempt: {@link OutboxStatus#PENDING} until {@code at}.
   *
   * @param at the earliest time of the next attempt
   * @return the new message
   */
  public OutboxMessage<T> retryAt(Instant at) {
    return toBuilder().status(OutboxStatus.PENDING).nextAttemptAt(at).build();
  }

  /**
   * The message given up on: {@link OutboxStatus#FAILED}, without a next attempt. A FAILED row
   * blocks its key until it is requeued.
   *
   * @param error the reason
   * @return the new message
   */
  public OutboxMessage<T> failed(String error) {
    return toBuilder().status(OutboxStatus.FAILED).error(error).nextAttemptAt(null).build();
  }

  /**
   * The message handed back unsent: {@link OutboxStatus#PENDING}, claimable again from {@code at},
   * with its attempts unchanged. The claim marker stays so the store can fence the update.
   *
   * @param at when it becomes claimable again
   * @return the new message
   */
  public OutboxMessage<T> released(Instant at) {
    return toBuilder().status(OutboxStatus.PENDING).claimedAt(null).nextAttemptAt(at).build();
  }

  /**
   * Builder of {@link OutboxMessage}; every setter returns the builder.
   *
   * @param <T> the message body type
   */
  public static final class Builder<T> {
    private UUID id;
    private Long seq;
    private String origin;
    private String routingKey;
    private String topic;
    private String key;
    private T message;
    private OutboxStatus status = OutboxStatus.PENDING;
    private int attempts;
    private String error;
    private Instant occurredAt;
    private Instant nextAttemptAt;
    private Instant publishedAt;
    private Instant claimedAt;
    private String claimedBy;
    private Instant sendStartedAt;
    private String traceparent;

    private Builder() {}

    private Builder<T> copyOf(OutboxMessage<T> source) {
      return id(source.id)
          .seq(source.seq)
          .origin(source.origin)
          .routingKey(source.routingKey)
          .topic(source.topic)
          .key(source.key)
          .message(source.message)
          .copyStateOf(source);
    }

    private Builder<T> copyStateOf(OutboxMessage<T> source) {
      return status(source.status)
          .attempts(source.attempts)
          .error(source.error)
          .occurredAt(source.occurredAt)
          .nextAttemptAt(source.nextAttemptAt)
          .publishedAt(source.publishedAt)
          .claimedAt(source.claimedAt)
          .claimedBy(source.claimedBy)
          .sendStartedAt(source.sendStartedAt)
          .traceparent(source.traceparent);
    }

    /**
     * Sets the id.
     *
     * @param id the row id
     * @return this builder
     */
    public Builder<T> id(UUID id) {
      this.id = id;
      return this;
    }

    /**
     * Sets the insertion order.
     *
     * @param seq the database-assigned sequence
     * @return this builder
     */
    public Builder<T> seq(Long seq) {
      this.seq = seq;
      return this;
    }

    /**
     * Sets the origin.
     *
     * @param origin the application that wrote the row
     * @return this builder
     */
    public Builder<T> origin(String origin) {
      this.origin = origin;
      return this;
    }

    /**
     * Sets the routing key.
     *
     * @param routingKey the AMQP routing key, or null
     * @return this builder
     */
    public Builder<T> routingKey(String routingKey) {
      this.routingKey = routingKey;
      return this;
    }

    /**
     * Sets the topic.
     *
     * @param topic the destination topic or exchange
     * @return this builder
     */
    public Builder<T> topic(String topic) {
      this.topic = topic;
      return this;
    }

    /**
     * Sets the per-key ordering key.
     *
     * @param key the message key, or null
     * @return this builder
     */
    public Builder<T> key(String key) {
      this.key = key;
      return this;
    }

    /**
     * Sets the body.
     *
     * @param message the body
     * @return this builder
     */
    public Builder<T> message(T message) {
      this.message = message;
      return this;
    }

    /**
     * Sets the status.
     *
     * @param status the lifecycle status
     * @return this builder
     */
    public Builder<T> status(OutboxStatus status) {
      this.status = status;
      return this;
    }

    /**
     * Sets the attempts.
     *
     * @param attempts the attempts charged so far
     * @return this builder
     */
    public Builder<T> attempts(int attempts) {
      this.attempts = attempts;
      return this;
    }

    /**
     * Sets the error.
     *
     * @param error the class name of the last failure, or null
     * @return this builder
     */
    public Builder<T> error(String error) {
      this.error = error;
      return this;
    }

    /**
     * Sets the creation time.
     *
     * @param occurredAt when the row was created
     * @return this builder
     */
    public Builder<T> occurredAt(Instant occurredAt) {
      this.occurredAt = occurredAt;
      return this;
    }

    /**
     * Sets the next attempt time.
     *
     * @param nextAttemptAt the earliest next claim, or null
     * @return this builder
     */
    public Builder<T> nextAttemptAt(Instant nextAttemptAt) {
      this.nextAttemptAt = nextAttemptAt;
      return this;
    }

    /**
     * Sets the publish time.
     *
     * @param publishedAt when the broker confirmed the row, or null
     * @return this builder
     */
    public Builder<T> publishedAt(Instant publishedAt) {
      this.publishedAt = publishedAt;
      return this;
    }

    /**
     * Sets the claim time.
     *
     * @param claimedAt when the claim was taken, or null
     * @return this builder
     */
    public Builder<T> claimedAt(Instant claimedAt) {
      this.claimedAt = claimedAt;
      return this;
    }

    /**
     * Sets the claim marker.
     *
     * @param claimedBy the claim marker, or null
     * @return this builder
     */
    public Builder<T> claimedBy(String claimedBy) {
      this.claimedBy = claimedBy;
      return this;
    }

    /**
     * Sets the send mark.
     *
     * @param sendStartedAt when the send started, or null
     * @return this builder
     */
    public Builder<T> sendStartedAt(Instant sendStartedAt) {
      this.sendStartedAt = sendStartedAt;
      return this;
    }

    /**
     * Sets the stored trace context.
     *
     * @param traceparent the W3C {@code traceparent}, or null
     * @return this builder
     */
    public Builder<T> traceparent(String traceparent) {
      this.traceparent = traceparent;
      return this;
    }

    /**
     * Builds the message.
     *
     * @return the immutable message
     */
    public OutboxMessage<T> build() {
      return new OutboxMessage<>(
          id,
          seq,
          origin,
          routingKey,
          topic,
          key,
          message,
          status,
          attempts,
          error,
          occurredAt,
          nextAttemptAt,
          publishedAt,
          claimedAt,
          claimedBy,
          sendStartedAt,
          traceparent);
    }
  }
}
