package io.codehunters.jpa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.proxy.HibernateProxy;

/**
 * Base class for JPA entities in an infrastructure (persistence adapter) layer: a time-ordered UUID
 * version 7 id generated on persist, and an optimistic-locking {@code version} column.
 *
 * <p>Equality is by id and class, and works across Hibernate proxies without initializing them. Two
 * entities that were never persisted are equal only to themselves. {@link #hashCode()} is the same
 * for every instance of a class, so an entity stays findable in a hash set while its id goes from
 * absent to assigned.
 *
 * <p>Columns are {@code id} ({@code uuid}) and {@code version} ({@code bigint}). Domain models stay
 * free of this class: map between them in the adapter.
 */
@MappedSuperclass
public abstract class BaseEntity {

  @Id
  @GeneratedValue
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  /** For JPA and subclasses. */
  protected BaseEntity() {}

  /**
   * Returns the id.
   *
   * @return the id, empty until the entity is persisted
   */
  public Optional<UUID> getId() {
    return Optional.ofNullable(id);
  }

  /**
   * Returns the optimistic-locking version.
   *
   * @return the version, empty until the entity is persisted
   */
  public Optional<Long> getVersion() {
    return Optional.ofNullable(version);
  }

  /**
   * Tells whether the entity was never persisted.
   *
   * @return {@code true} while no id has been assigned
   */
  public boolean isNew() {
    return id == null;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BaseEntity that) || persistentClass(this) != persistentClass(that)) {
      return false;
    }
    var mine = idOf(this);
    return mine != null && mine.equals(idOf(that));
  }

  @Override
  public int hashCode() {
    return persistentClass(this).hashCode();
  }

  private static Class<?> persistentClass(Object entity) {
    return entity instanceof HibernateProxy proxy
        ? proxy.getHibernateLazyInitializer().getPersistentClass()
        : entity.getClass();
  }

  private static Object idOf(BaseEntity entity) {
    return entity instanceof HibernateProxy proxy
        ? proxy.getHibernateLazyInitializer().getInternalIdentifier()
        : entity.id;
  }
}
