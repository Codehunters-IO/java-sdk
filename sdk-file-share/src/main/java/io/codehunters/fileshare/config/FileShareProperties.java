package io.codehunters.fileshare.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the file-share SDK, bound from the {@code vitxo.file-share} property prefix.
 *
 * @param enabled whether the SDK auto-configuration is active
 * @param baseUrl the file-share service base URL; required when {@code enabled}
 * @param connectTimeout the connection timeout
 * @param readTimeout the read timeout
 */
@ConfigurationProperties(prefix = "vitxo.file-share")
public record FileShareProperties(
    @DefaultValue("true") boolean enabled,
    String baseUrl,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("30s") Duration readTimeout) {}
