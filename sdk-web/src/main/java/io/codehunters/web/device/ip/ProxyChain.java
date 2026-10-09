package io.codehunters.web.device.ip;

import java.util.List;
import java.util.Objects;

/**
 * What a request says about its path through proxies: the connection's remote address and the raw
 * {@code Forwarded} and {@code X-Forwarded-For} header lines, in the order they were received.
 *
 * @param remoteAddress the address of the peer that opened the connection
 * @param forwarded the {@code Forwarded} header lines (RFC 7239)
 * @param xForwardedFor the {@code X-Forwarded-For} header lines
 */
public record ProxyChain(String remoteAddress, List<String> forwarded, List<String> xForwardedFor) {

  /** Copies the header lines, dropping null ones. */
  public ProxyChain {
    if (forwarded == null || xForwardedFor == null) {
      throw new IllegalArgumentException("header lists must not be null");
    }
    forwarded = withoutNulls(forwarded);
    xForwardedFor = withoutNulls(xForwardedFor);
  }

  private static List<String> withoutNulls(List<String> lines) {
    return lines.stream().filter(Objects::nonNull).toList();
  }
}
