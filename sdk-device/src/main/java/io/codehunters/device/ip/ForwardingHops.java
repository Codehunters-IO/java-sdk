package io.codehunters.device.ip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Splits the configured forwarding header into hops, client first. Each hop is the raw node text a
 * proxy wrote; {@code Forwarded} elements without a {@code for} parameter become empty hops so they
 * stop the walk instead of being skipped.
 *
 * <p>Proxies append to the line the client sent ({@code ", 203.0.113.9"}), so client text must
 * never be able to swallow what follows it. {@code X-Forwarded-For} has no quoted strings and is
 * split on every comma. A {@code Forwarded} line whose quotes do not balance is split on every
 * comma too: only the client's own elements on the left become invalid.
 */
final class ForwardingHops {

  private ForwardingHops() {}

  static List<String> of(ProxyChain chain, ForwardingHeader header) {
    if (header == ForwardingHeader.FORWARDED) {
      return fromForwarded(chain.forwarded());
    }
    return chain.xForwardedFor().stream().flatMap(line -> splitEverywhere(line, ',')).toList();
  }

  private static List<String> fromForwarded(List<String> lines) {
    return lines.stream()
        .flatMap(line -> split(line, ',').stream())
        .map(ForwardingHops::forNode)
        .toList();
  }

  private static String forNode(String element) {
    for (var pair : split(element, ';')) {
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

  private static List<String> split(String text, char separator) {
    return splitOutsideQuotes(text, separator)
        .orElseGet(() -> splitEverywhere(text, separator).toList());
  }

  private static Stream<String> splitEverywhere(String text, char separator) {
    return Arrays.stream(text.split(String.valueOf(separator), -1)).map(String::strip);
  }

  private static Optional<List<String>> splitOutsideQuotes(String text, char separator) {
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
    if (quoted) {
      return Optional.empty();
    }
    parts.add(current.toString().strip());
    return Optional.of(parts);
  }
}
