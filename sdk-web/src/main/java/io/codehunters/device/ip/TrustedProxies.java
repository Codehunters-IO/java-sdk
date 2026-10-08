package io.codehunters.device.ip;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * The proxies whose forwarding headers are believed: single addresses or CIDR blocks, IPv4 or IPv6.
 * An empty set trusts nobody, so only the connection's remote address is used.
 */
public final class TrustedProxies {

  private final List<Cidr> blocks;

  private TrustedProxies(List<Cidr> blocks) {
    this.blocks = List.copyOf(blocks);
  }

  /**
   * Parses the trusted entries.
   *
   * @param entries addresses ({@code 10.0.0.7}) or blocks ({@code 10.0.0.0/8}, {@code
   *     2001:db8::/32})
   * @return the trusted proxies
   * @throws IllegalArgumentException when an entry is neither, carries a port, or is a {@code /0}
   *     block; the message names the entry's index but not its value
   */
  public static TrustedProxies of(List<String> entries) {
    if (entries == null) {
      throw new IllegalArgumentException("trusted proxies must not be null");
    }
    var blocks = new ArrayList<Cidr>(entries.size());
    for (int i = 0; i < entries.size(); i++) {
      blocks.add(block(entries.get(i), i));
    }
    return new TrustedProxies(blocks);
  }

  // A /0 block trusts every hop, so the client-controlled leftmost one would be returned.
  private static Cidr block(String entry, int index) {
    var block =
        Cidr.parse(entry)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "trusted proxy entry " + index + " is not an IP address or CIDR block"));
    if (block.prefixLength() == 0) {
      throw new IllegalArgumentException(
          "trusted proxy entry " + index + " trusts every address; list the proxies instead");
    }
    return block;
  }

  /**
   * Whether the address belongs to a trusted proxy.
   *
   * @param address the address to check
   * @return {@code true} when a trusted block contains it
   */
  public boolean contains(InetAddress address) {
    return blocks.stream().anyMatch(block -> block.contains(address));
  }

  /**
   * Whether nothing is trusted.
   *
   * @return {@code true} when no proxy is trusted
   */
  public boolean isEmpty() {
    return blocks.isEmpty();
  }
}
