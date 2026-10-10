package io.codehunters.web.device.servlet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Removes the {@code for} parameters from {@code Forwarded} lines (RFC 7239) and keeps the others
 * ({@code proto}, {@code host}, {@code by}, extensions). Commas and semicolons inside quoted
 * strings are not separators. A line whose quotes do not balance cannot be split safely and is
 * dropped whole, as is an element or a line left with no parameter.
 */
final class ForwardedForParameters {

  private ForwardedForParameters() {}

  static List<String> strip(List<String> lines) {
    return lines.stream().map(ForwardedForParameters::stripLine).flatMap(Optional::stream).toList();
  }

  private static Optional<String> stripLine(String line) {
    return splitOutsideQuotes(line, ',')
        .flatMap(ForwardedForParameters::stripElements)
        .filter(stripped -> !stripped.isEmpty());
  }

  private static Optional<String> stripElements(List<String> elements) {
    var kept = new ArrayList<String>();
    for (var element : elements) {
      var pairs = splitOutsideQuotes(element, ';').orElseThrow();
      var others = pairs.stream().filter(pair -> !isFor(pair) && !pair.isEmpty()).toList();
      if (!others.isEmpty()) {
        kept.add(String.join(";", others));
      }
    }
    return Optional.of(String.join(", ", kept));
  }

  private static boolean isFor(String pair) {
    var equals = pair.indexOf('=');
    var name = equals < 0 ? pair : pair.substring(0, equals);
    return "for".equals(name.strip().toLowerCase(Locale.ROOT));
  }

  private static Optional<List<String>> splitOutsideQuotes(String text, char separator) {
    var parts = new ArrayList<String>();
    var current = new StringBuilder();
    var quoted = false;
    for (int i = 0; i < text.length(); i++) {
      var c = text.charAt(i);
      if (c == separator && !quoted) {
        parts.add(current.toString().strip());
        current.setLength(0);
        continue;
      }
      if (quoted && c == '\\' && i + 1 < text.length()) {
        current.append(c);
        c = text.charAt(++i);
      } else if (c == '"') {
        quoted = !quoted;
      }
      current.append(c);
    }
    parts.add(current.toString().strip());
    return quoted ? Optional.empty() : Optional.of(parts);
  }
}
