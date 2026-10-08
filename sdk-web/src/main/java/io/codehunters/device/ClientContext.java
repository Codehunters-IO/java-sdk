package io.codehunters.device;

import io.codehunters.device.useragent.UserAgentInfo;
import java.net.InetAddress;
import java.util.Optional;

/**
 * Who is calling: the client address as resolved behind trusted proxies, and the parsed and raw
 * (sanitized, truncated) {@code User-Agent}.
 *
 * @param ip the client address, empty when it could not be determined
 * @param userAgent the parsed {@code User-Agent}
 * @param rawUserAgent the sanitized, truncated header; empty when absent
 */
public record ClientContext(
    Optional<InetAddress> ip, UserAgentInfo userAgent, String rawUserAgent) {

  /** Rejects null components. */
  public ClientContext {
    if (ip == null || userAgent == null || rawUserAgent == null) {
      throw new IllegalArgumentException("client context components must not be null");
    }
  }

  /**
   * The client address as text: dotted quad for IPv4, full colon-separated form for IPv6.
   *
   * @return the address, empty when unknown
   */
  public Optional<String> ipAddress() {
    return ip.map(InetAddress::getHostAddress);
  }
}
