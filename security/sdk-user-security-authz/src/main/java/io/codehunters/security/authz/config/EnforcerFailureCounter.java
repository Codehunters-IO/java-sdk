package io.codehunters.security.authz.config;

import java.util.function.Consumer;

/**
 * Counts failures of the policy enforcer as {@value #METER}, tagged by {@code stage}: {@code
 * enforcer} when it could not be built or failed while enforcing, {@code call} when a call to
 * Keycloak failed with an I/O error. Micrometer types stay out of this class, so the module runs
 * without Micrometer.
 */
final class EnforcerFailureCounter {

  static final String METER = "codehunters.authz.enforcer.failures";
  static final String ENFORCER = "enforcer";
  static final String CALL = "call";

  private static final EnforcerFailureCounter NONE = new EnforcerFailureCounter(stage -> {});

  private final Consumer<String> increment;

  EnforcerFailureCounter(Consumer<String> increment) {
    this.increment = increment;
  }

  static EnforcerFailureCounter none() {
    return NONE;
  }

  void increment(String stage) {
    increment.accept(stage);
  }
}
