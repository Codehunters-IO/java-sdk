package io.codehunters.device.ip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Splits the configured forwarding header into hops, client first. Each hop is the raw node text a
 * proxy wrote; {@code Forwarded} elements without a {@code for} parameter become empty hops so they
 * stop the walk instead of being skipped.
 */
final class ForwardingHops {

  private ForwardingHops() {}

  static List<String> of(ProxyChain chain, ForwardingHeader header) {
    if (header == ForwardingHeader.FORWARDED) {
      return fromForwarded(chain.forwarded());
    }
    return chain.xForwardedFor().stream()
        .flatMap(line -> splitOutsideQuotes(line, ',').stream())
        .toList();
  }

  private static List<String> fromForwarded(List<String> lines) {
    return lines.stream()
        .flatMap(line -> splitOutsideQuotes(line, ',').stream())
        .map(ForwardingHops::forNode)
        .toList();
  }

  private static String forNode(String element) {
    for (var pair : splitOutsideQuotes(element, ';')) {
      var equals = pair.indexOf('=');
      if (equals > 0 && "for".equals(pair.substring(0, equals).strip().toLowerCase(Locale.ROOT))) {
        return unquote(pair.substring(equals + 1).strip());
      }
    }
    return "";
  }

  // An unterminated quoted string is not a valid node, so it yields an empty hop.
  private static String unquote(String value) {
    if (!value.startsWith("\"")) {
      return value;
    }
    var out = new StringBuilder();
    for (int i = 1; i < value.length(); i++) {
      var c = value.charAt(i);
      if (c == '"') {
        return i == value.length() - 1 ? out.toString() : "";
      }
      if (c == '\\' && i + 1 < value.length()) {
        c = value.charAt(++i);
      }
      out.append(c);
    }
    return "";
  }

  static List<String> splitOutsideQuotes(String text, char separator) {
    var parts = new ArrayList<String>();
    var current = new StringBuilder();
    var quoted = false;
    for (int i = 0; i < text.length(); i++) {
      var c = text.charAt(i);
      if (quoted && c == '\\' && i + 1 < text.length()) {
        current.append(c).append(text.charAt(++i));
        continue;
      }
      if (c == '"') {
        quoted = !quoted;
      }
      if (c == separator && !quoted) {
        parts.add(current.toString().strip());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    parts.add(current.toString().strip());
    return parts;
  }
}
