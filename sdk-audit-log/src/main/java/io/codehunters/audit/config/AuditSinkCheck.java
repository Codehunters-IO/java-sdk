package io.codehunters.audit.config;

import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/** Fails startup when {@code codehunters.audit.sink} names a sink that does not exist. */
final class AuditSinkCheck {

  static final String SINK = "codehunters.audit.sink";

  private static final Set<String> SINKS = Set.of("jdbc", "outbox");

  private AuditSinkCheck() {}

  /**
   * Accepts an unset sink, {@code jdbc} or {@code outbox}, in any case.
   *
   * @param environment the application environment
   * @throws IllegalStateException naming the key and the allowed values otherwise
   */
  static void validate(Environment environment) {
    String sink = Binder.get(environment).bind(SINK, String.class).orElse(null);
    if (sink != null && !SINKS.contains(sink.toLowerCase(Locale.ROOT))) {
      throw new IllegalStateException(SINK + " must be one of jdbc, outbox; got '" + sink + "'");
    }
  }
}
