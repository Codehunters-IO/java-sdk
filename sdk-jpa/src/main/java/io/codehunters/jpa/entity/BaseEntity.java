package io.codehunters.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.domain.Persistable;

/**
 * Base class for JPA entities in an infrastructure (persistence adapter) layer: a time-ordered UUID
 * version 7 id generated on persist, and an optimistic-locking {@code version} column.
 *
 * <p>It is a Spring Data {@link Persistable}: an entity is new while it has no id, so a
 * repository's {@code save} persists the given instance and Hibernate assigns its id, instead of
 * merging a copy. Read the id with {@link #id()} and the version with {@link #version()}; {@link
 * #getId()} exists for the {@link Persistable} contract and returns {@code null} while the entity
 * is new.
 *
 * <p>Equality is by persistent class and id. {@link #equals(Object)}, {@link #hashCode()}, {@link
 * #id()} and {@link #isNew()} are final, so on an uninitialized Hibernate proxy they run on the
 * proxy itself and read its identifier without loading the row, also after the session closed. Two
 * entities that were never persisted are equal only to themselves. {@link #hashCode()} is the same
 * for every instance of a class, so an entity stays findable in a hash set while its id goes from
 * absent to assigned.
 *
 * <p>With entity inheritance, a proxy created for the parent type reports the parent as its class,
 * so it does not equal a loaded instance of the subtype for the same row. Compare ids, or load the
 * proxy first, when that matters.
 *
 * <p>Columns are {@code id} ({@code uuid}) and {@code version} ({@code bigint}). Domain models stay
 * free of this class: map between them in the adapter.
 */
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

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
   * Returns the id for Spring Data's {@link Persistable} contract. Prefer {@link #id()}. Hibernate
   * recognises this getter, so on an uninitialized proxy it answers without loading the row.
   *
   * @return the id, or {@code null} until the entity is persisted
   */
  @Override
  public UUID getId() {
    return id;
  }

  /**
   * Returns the id. On an uninitialized proxy it is read from the proxy, without loading the row.
   *
   * @return the id, empty until the entity is persisted
   */
  public final Optional<UUID> id() {
    return Optional.ofNullable(idOf(this));
  }

  /**
   * Returns the optimistic-locking version. On an uninitialized proxy it loads the row.
   *
   * @return the version, empty until the entity is persisted
   */
  public Optional<Long> version() {
    return Optional.ofNullable(version);
  }

  /**
   * Tells whether the entity was never persisted; Spring Data's repositories persist a new entity
   * and merge any other.
   *
   * @return {@code true} while no id has been assigned
   */
  @Override
  public final boolean isNew() {
    return idOf(this) == null;
  }

  @Override
  public final boolean equals(Object other) {
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
  public final int hashCode() {
    return persistentClass(this).hashCode();
  }

  private static Class<?> persistentClass(Object entity) {
    return entity instanceof HibernateProxy proxy
        ? proxy.getHibernateLazyInitializer().getPersistentClass()
        : entity.getClass();
  }

  private static UUID idOf(BaseEntity entity) {
    return entity instanceof HibernateProxy proxy
        ? (UUID) proxy.getHibernateLazyInitializer().getInternalIdentifier()
        : entity.id;
  }
}
