package io.codehunters.device.ip;

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

  Cidr {
    network = network.clone();
  }

  static Optional<Cidr> parse(String entry) {
    var parts = entry.split("/", -1);
    if (parts.length > 2) {
      return Optional.empty();
    }
    var address = IpLiterals.parse(parts[0]).map(InetAddress::getAddress);
    if (address.isEmpty()) {
      return Optional.empty();
    }
    var maxBits = address.get().length * 8;
    var bits = parts.length == 1 ? Optional.of(maxBits) : prefix(parts[1], maxBits);
    return bits.map(prefix -> new Cidr(address.get(), prefix));
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
