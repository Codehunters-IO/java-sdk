package io.codehunters.device.ip;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the client address of a request that may have crossed proxies.
 *
 * <p>Forwarding headers are believed only as far as trusted proxies wrote them. Starting from the
 * connection's remote address, the hops are read right to left while the current address is a
 * trusted proxy; the first untrusted address is the client. A hop that is not an IP literal ({@code
 * unknown}, an obfuscated identifier, garbage) stops the walk, and the last address with a known
 * origin is returned, because anything left of it may have been written by the client. When every
 * hop is trusted, the leftmost one is returned. Only the configured header is read, since a client
 * can send the other one through proxies that leave it untouched. No host name is ever resolved.
 */
public final class ClientIpResolver {

  private final TrustedProxies trustedProxies;
  private final ForwardingHeader header;

  /**
   * Creates a resolver.
   *
   * @param trustedProxies the proxies whose headers are believed; empty trusts nobody
   * @param header the header those proxies write
   */
  public ClientIpResolver(TrustedProxies trustedProxies, ForwardingHeader header) {
    if (trustedProxies == null || header == null) {
      throw new IllegalArgumentException("trusted proxies and forwarding header must not be null");
    }
    this.trustedProxies = trustedProxies;
    this.header = header;
  }

  /**
   * Resolves the client address.
   *
   * @param chain the request's remote address and forwarding headers
   * @return the client address, or empty when the remote address is not an IP literal
   */
  public Optional<InetAddress> resolve(ProxyChain chain) {
    if (chain == null) {
      throw new IllegalArgumentException("proxy chain must not be null");
    }
    return IpLiterals.parse(withoutZone(chain.remoteAddress())).map(remote -> walk(remote, chain));
  }

  // Containers report link-local IPv6 peers with their interface (fe80::1%en0). The zone id is
  // dropped for the connection's own address only; hops written into headers keep being rejected.
  private static String withoutZone(String remoteAddress) {
    if (remoteAddress == null) {
      return null;
    }
    var zone = remoteAddress.indexOf('%');
    return zone > 0 && remoteAddress.contains(":")
        ? remoteAddress.substring(0, zone)
        : remoteAddress;
  }

  private InetAddress walk(InetAddress remote, ProxyChain chain) {
    if (trustedProxies.isEmpty()) {
      return remote;
    }
    List<String> hops = ForwardingHops.of(chain, header);
    var current = remote;
    for (int i = hops.size() - 1; i >= 0 && trustedProxies.contains(current); i--) {
      var hop = IpLiterals.parse(hops.get(i));
      if (hop.isEmpty()) {
        return current;
      }
      current = hop.get();
    }
    return current;
  }
}
