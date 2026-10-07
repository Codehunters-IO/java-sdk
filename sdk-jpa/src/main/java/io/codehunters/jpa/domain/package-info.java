/**
 * Base classes for JPA entities: {@link io.codehunters.jpa.domain.BaseEntity} (UUID v7 id,
 * optimistic locking, proxy-safe equality) and {@link io.codehunters.jpa.domain.AuditableEntity}
 * (who and when). They belong to persistence adapters, never to the domain model.
 */
package io.codehunters.jpa.domain;
