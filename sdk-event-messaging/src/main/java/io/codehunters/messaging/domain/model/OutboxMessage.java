package io.codehunters.messaging.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persistent outbox row: a message awaiting publication to the broker, with retry bookkeeping and a
 * claim-based dispatch lifecycle.
 *
 * @param <T> the message body type as persisted (typically {@code String} after serialization)
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
public class OutboxMessage<T> {
  private UUID id;
  private String origin;
  private String routingKey;
  private String topic;
  private String key;
  private T message;
  @Builder.Default private OutboxStatus status = OutboxStatus.PENDING;
  @Builder.Default private int attempts = 0;
  private String error;
  @Builder.Default private LocalDateTime occurredAt = LocalDateTime.now();
  private LocalDateTime nextAttemptAt;
  private LocalDateTime publishedAt;
  private LocalDateTime claimedAt;
  private String claimedBy;
}
