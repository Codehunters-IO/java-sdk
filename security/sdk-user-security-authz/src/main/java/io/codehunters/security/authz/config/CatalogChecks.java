package io.codehunters.security.authz.config;

import io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties.EnforcementMode;
import io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties.PathMapping;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Startup checks that tie the path catalog to the paths the enforcer never sees.
 *
 * <p>Anonymous requests (public and excluded paths) and skipped paths are never enforced, so an
 * enforced catalogued path that one of them can also match would be protected only part of the
 * time. Two patterns overlap when some request path could match both. The check is conservative: a
 * variable or wildcard segment is taken to match any segment, whatever its regular expression, and
 * a trailing {@code **} or capture-all variable any remainder, so patterns that differ only in a
 * variable's constraint are reported too. Catalogued paths mapped as {@code DISABLED} are never
 * enforced and therefore allowed.
 */
final class CatalogChecks {

  private static final String ANY_REMAINDER = "**";
  // Keycloak's trailing "/*" matches any remainder; in Spring only "**" and "{*name}" do.
  private static final Predicate<String> KEYCLOAK_REMAINDER = "*"::equals;
  private static final Predicate<String> SPRING_REMAINDER = segment -> segment.startsWith("{*");

  private CatalogChecks() {}

  /**
   * Fails when one of the patterns overlaps an enforced catalogued path.
   *
   * @param property the property the patterns come from, named in the failure
   * @param patterns path patterns in Spring's syntax
   * @param catalog the enforcer's path catalog, in Keycloak's syntax
   */
  static void requireNoOverlap(String property, List<String> patterns, List<PathMapping> catalog) {
    catalog.stream()
        .filter(mapping -> mapping.enforcementMode() != EnforcementMode.DISABLED)
        .map(PathMapping::path)
        .forEach(catalogued -> requireNoneOverlaps(property, patterns, catalogued));
  }

  private static void requireNoneOverlaps(
      String property, List<String> patterns, String catalogued) {
    String[] enforced = segments(catalogued, KEYCLOAK_REMAINDER);
    patterns.stream()
        .filter(pattern -> overlap(segments(pattern, SPRING_REMAINDER), enforced, 0))
        .findFirst()
        .ifPresent(pattern -> fail(property, pattern, catalogued));
  }

  // Segment-wise unification: literals must be equal, a wildcard segment matches any segment and a
  // trailing ** matches any remainder, including none.
  private static boolean overlap(String[] left, String[] right, int index) {
    if (isRemainder(left, index) || isRemainder(right, index)) {
      return true;
    }
    if (index == left.length || index == right.length) {
      return left.length == right.length;
    }
    return segmentsOverlap(left[index], right[index]) && overlap(left, right, index + 1);
  }

  private static boolean isRemainder(String[] segments, int index) {
    return index < segments.length && ANY_REMAINDER.equals(segments[index]);
  }

  private static boolean segmentsOverlap(String left, String right) {
    return isWildcard(left) || isWildcard(right) || left.equals(right);
  }

  private static boolean isWildcard(String segment) {
    return segment.indexOf('{') >= 0 || segment.indexOf('*') >= 0 || segment.indexOf('?') >= 0;
  }

  // A trailing segment that covers any remainder becomes **.
  private static String[] segments(String pattern, Predicate<String> remainder) {
    String[] parts =
        Arrays.stream(pattern.split("/")).filter(part -> !part.isEmpty()).toArray(String[]::new);
    if (parts.length > 0 && remainder.test(parts[parts.length - 1])) {
      parts[parts.length - 1] = ANY_REMAINDER;
    }
    return parts;
  }

  private static void fail(String property, String pattern, String catalogued) {
    throw new IllegalStateException(
        ("%s entry %s overlaps the catalogued path %s (codehunters.security.authz.paths), which"
                + " the enforcer would then protect only part of the time. Remove the path from"
                + " one of the two lists, or map the catalogued path as DISABLED.")
            .formatted(property, pattern, catalogued));
  }
}
