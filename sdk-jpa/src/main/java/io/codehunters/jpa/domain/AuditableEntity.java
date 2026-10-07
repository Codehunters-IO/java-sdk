package io.codehunters.jpa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A {@link BaseEntity} that records who created and last changed it, and when, through Spring Data
 * JPA auditing. The SDK enables auditing with the application's {@link java.time.Clock} and the
 * signed-in user; see the module README.
 *
 * <p>Columns are {@code created_at} and {@code updated_at} ({@code timestamp with time zone}),
 * {@code created_by} and {@code updated_by} ({@code varchar(255)}). The creation columns are never
 * updated. Rename them in a subclass with {@code @AttributeOverride}. Accessors are record style,
 * like {@link BaseEntity}'s.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity extends BaseEntity {

  @CreatedDate
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @LastModifiedDate
  @Column(name = "updated_at")
  private Instant updatedAt;

  @LastModifiedBy
  @Column(name = "updated_by")
  private String updatedBy;

  /** For JPA and subclasses. */
  protected AuditableEntity() {}

  /**
   * Returns when the entity was created.
   *
   * @return the creation instant, empty until persisted or when auditing is off
   */
  public Optional<Instant> createdAt() {
    return Optional.ofNullable(createdAt);
  }

  /**
   * Returns who created the entity.
   *
   * @return the author, empty when nobody was signed in
   */
  public Optional<String> createdBy() {
    return Optional.ofNullable(createdBy);
  }

  /**
   * Returns when the entity last changed; the creation instant until the first update.
   *
   * @return the last modification instant, empty until persisted or when auditing is off
   */
  public Optional<Instant> updatedAt() {
    return Optional.ofNullable(updatedAt);
  }

  /**
   * Returns who last changed the entity.
   *
   * @return the last author, empty when nobody was signed in
   */
  public Optional<String> updatedBy() {
    return Optional.ofNullable(updatedBy);
  }
}
