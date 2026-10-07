package io.codehunters.device.config;

import io.codehunters.device.ip.TrustedProxies;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the device SDK, bound from the {@code codehunters.device} prefix. Invalid
 * values fail at startup.
 *
 * @param enabled whether the SDK auto-configuration is active
 * @param trustedProxies addresses or CIDR blocks of the proxies whose {@code Forwarded} and {@code
 *     X-Forwarded-For} headers are believed; empty (the default) trusts nobody and uses the
 *     connection's remote address
 * @param userAgent {@code User-Agent} handling
 */
@ConfigurationProperties(prefix = "codehunters.device")
public record DeviceProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue List<String> trustedProxies,
    @DefaultValue UserAgent userAgent) {

  /** Validates the trusted proxies. */
  public DeviceProperties {
    try {
      TrustedProxies.of(trustedProxies);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("codehunters.device.trusted-proxies: " + e.getMessage());
    }
    trustedProxies = List.copyOf(trustedProxies);
  }

  /**
   * {@code User-Agent} handling.
   *
   * @param maxLength characters of the header kept and parsed, 16 to 8192; longer headers are cut
   *     so regex parsing stays cheap
   * @param cacheSize parsed headers kept in memory, 0 to 100000; 0 disables the cache
   */
  public record UserAgent(@DefaultValue("512") int maxLength, @DefaultValue("1024") int cacheSize) {

    /** Validates the ranges. */
    public UserAgent {
      if (maxLength < 16 || maxLength > 8192) {
        throw new IllegalArgumentException(
            "codehunters.device.user-agent.max-length must be between 16 and 8192");
      }
      if (cacheSize < 0 || cacheSize > 100_000) {
        throw new IllegalArgumentException(
            "codehunters.device.user-agent.cache-size must be between 0 and 100000");
      }
    }
  }
}
