package io.codehunters.messaging.domain.model;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

  /**
   * Database-assigned insertion order; per-key ordering uses it. Read-only: never inserted, {@code
   * null} until the row is read back.
   */
  private Long seq;

  private String origin;
  private String routingKey;
  private String topic;
  private String key;
  private T message;
  @Builder.Default private OutboxStatus status = OutboxStatus.PENDING;
  @Builder.Default private int attempts = 0;
  private String error;
  @Builder.Default private LocalDateTime occurredAt = LocalDateTime.now(ZoneOffset.UTC);
  private LocalDateTime nextAttemptAt;
  private LocalDateTime publishedAt;
  private LocalDateTime claimedAt;
  private String claimedBy;

  /**
   * When the claim owner started handing this row to the broker; {@code null} until then. A reclaim
   * charges an attempt only when it is set, so rows of an abandoned batch that were never sent keep
   * their attempt count.
   */
  private LocalDateTime sendStartedAt;
}
