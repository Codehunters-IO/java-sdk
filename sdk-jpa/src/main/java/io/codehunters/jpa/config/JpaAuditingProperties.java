package io.codehunters.jpa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JPA auditing settings, bound from {@code codehunters.jpa.auditing}.
 *
 * @param enabled whether the SDK enables Spring Data JPA auditing; it never does when the
 *     application enables it itself
 */
@ConfigurationProperties(prefix = "codehunters.jpa.auditing")
public record JpaAuditingProperties(@DefaultValue("true") boolean enabled) {}
