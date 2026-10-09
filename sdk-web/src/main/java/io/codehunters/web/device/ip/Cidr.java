package io.codehunters.web.device.ip;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One trusted block: an address and the number of leading bits that must match. Only used for
 * membership checks, so it keeps the record's identity-based equality.
 */
record Cidr(byte[] network, int prefixLength) {

  private static final Pattern PREFIX = Pattern.compile("0|[1-9][0-9]{0,2}");
  // The JDK turns an IPv4-mapped literal (::ffff:a.b.c.d) into a 4-byte address, so its prefix is
  // counted from bit 96 of the IPv6 form.
  private static final int IPV4_MAPPED_PREFIX = 96;

  Cidr {
    network = network.clone();
  }

  static Optional<Cidr> parse(String entry) {
    var parts = entry.split("/", -1);
    if (parts.length > 2) {
      return Optional.empty();
    }
    var address = IpLiterals.parseWithoutPort(parts[0]).map(InetAddress::getAddress);
    if (address.isEmpty()) {
      return Optional.empty();
    }
    var offset = parts[0].contains(":") && address.get().length == 4 ? IPV4_MAPPED_PREFIX : 0;
    var maxBits = address.get().length * 8 + offset;
    var bits = parts.length == 1 ? Optional.of(maxBits) : prefix(parts[1], maxBits);
    return bits.filter(prefix -> prefix >= offset)
        .map(prefix -> new Cidr(address.get(), prefix - offset));
  }

  private static Optional<Integer> prefix(String text, int maxBits) {
    if (!PREFIX.matcher(text).matches() || Integer.parseInt(text) > maxBits) {
      return Optional.empty();
    }
    return Optional.of(Integer.parseInt(text));
  }

  boolean contains(InetAddress address) {
    var candidate = address.getAddress();
    if (candidate.length != network.length) {
      return false;
    }
    var fullBytes = prefixLength / 8;
    if (!Arrays.equals(network, 0, fullBytes, candidate, 0, fullBytes)) {
      return false;
    }
    var remainingBits = prefixLength % 8;
    if (remainingBits == 0) {
      return true;
    }
    var mask = 0xFF << (8 - remainingBits);
    return (network[fullBytes] & mask) == (candidate[fullBytes] & mask);
  }
}
