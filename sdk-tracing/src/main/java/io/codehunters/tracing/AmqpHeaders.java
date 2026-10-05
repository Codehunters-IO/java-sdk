package io.codehunters.tracing;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.amqp.core.MessageProperties;

/** Reads AMQP headers by name regardless of case, decoding byte values as UTF-8. */
final class AmqpHeaders {

  private AmqpHeaders() {}

  static String get(MessageProperties properties, String name) {
    return properties.getHeaders().entrySet().stream()
        .filter(header -> header.getKey().equalsIgnoreCase(name))
        .map(Map.Entry::getValue)
        .filter(value -> value != null)
        .map(AmqpHeaders::asString)
        .findFirst()
        .orElse(null);
  }

  /** Replaces every casing of the header with a single lower-case one. */
  static void set(MessageProperties properties, String name, String value) {
    properties.getHeaders().keySet().removeIf(key -> key.equalsIgnoreCase(name));
    properties.setHeader(name, value);
  }

  private static String asString(Object value) {
    return value instanceof byte[] bytes
        ? new String(bytes, StandardCharsets.UTF_8)
        : value.toString();
  }
}
