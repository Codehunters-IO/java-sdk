package io.codehunters.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the audit SDK, bound from the {@code vitxo.audit} prefix.
 *
 * @param failOnError whether audit persistence failures should propagate
 */
@ConfigurationProperties(prefix = "vitxo.audit")
public record AuditProperties(@DefaultValue("false") boolean failOnError) {}
