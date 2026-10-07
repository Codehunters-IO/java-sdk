package io.codehunters.device.ip;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parses IP address literals without ever resolving a host name. Accepts IPv4 dotted quads, IPv6
 * (optionally in brackets) and an optional trailing port, the forms proxies write into {@code
 * Forwarded} and {@code X-Forwarded-For}. Anything else, including host names, {@code unknown} and
 * obfuscated identifiers, is rejected.
 */
public final class IpLiterals {

  private static final Pattern IPV4_PART = Pattern.compile("0|[1-9][0-9]{0,2}");
  private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]{2,45}");
  private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");
  private static final int MAX_PORT = 65_535;

  private IpLiterals() {}

  /**
   * Parses an address literal.
   *
   * @param text the literal, possibly with a port
   * @return the address, or empty when the text is not an IP literal
   */
  public static Optional<InetAddress> parse(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    var node = text.strip();
    if (node.startsWith("[")) {
      return parseBracketed(node);
    }
    var colons = node.chars().filter(c -> c == ':').count();
    if (colons == 1) {
      return parseWithPort(node);
    }
    return colons == 0 ? parseIpv4(node) : parseIpv6(node);
  }

  private static Optional<InetAddress> parseBracketed(String node) {
    var close = node.indexOf(']');
    if (close < 0 || !isPortSuffix(node.substring(close + 1))) {
      return Optional.empty();
    }
    return parseIpv6(node.substring(1, close));
  }

  private static Optional<InetAddress> parseWithPort(String node) {
    var colon = node.indexOf(':');
    if (!isPortSuffix(node.substring(colon))) {
      return Optional.empty();
    }
    return parseIpv4(node.substring(0, colon));
  }

  private static boolean isPortSuffix(String suffix) {
    if (suffix.isEmpty()) {
      return true;
    }
    var digits = suffix.substring(1);
    return suffix.charAt(0) == ':'
        && PORT.matcher(digits).matches()
        && Integer.parseInt(digits) <= MAX_PORT;
  }

  private static Optional<InetAddress> parseIpv4(String node) {
    var parts = node.split("\\.", -1);
    if (parts.length != 4) {
      return Optional.empty();
    }
    var bytes = new byte[4];
    for (int i = 0; i < 4; i++) {
      if (!IPV4_PART.matcher(parts[i]).matches() || Integer.parseInt(parts[i]) > 255) {
        return Optional.empty();
      }
      bytes[i] = (byte) Integer.parseInt(parts[i]);
    }
    return byAddress(bytes);
  }

  // A literal containing ':' is parsed as IPv6 by the JDK and never looked up in DNS.
  private static Optional<InetAddress> parseIpv6(String node) {
    if (!node.contains(":") || !IPV6.matcher(node).matches()) {
      return Optional.empty();
    }
    try {
      return Optional.of(InetAddress.getByName(node));
    } catch (UnknownHostException e) {
      return Optional.empty();
    }
  }

  private static Optional<InetAddress> byAddress(byte[] bytes) {
    try {
      return Optional.of(InetAddress.getByAddress(bytes));
    } catch (UnknownHostException e) {
      return Optional.empty();
    }
  }
}
