package io.codehunters.security.authz.config;

import io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties.EnforcementMode;
import io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties.PathMapping;
import java.util.List;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** Startup checks that tie the path catalog to sdk-user-security's path settings. */
final class CatalogChecks {

  private CatalogChecks() {}

  /**
   * Fails when a public path covers a catalogued one that is enforced: anonymous requests are never
   * enforced, so the catalogued path would be protected only for callers who happen to send a
   * token. Public routes mapped as {@code DISABLED} (so that callers sending a token are not
   * denied) are consistent and allowed.
   */
  static void requireNoPublicPathCovers(List<String> publicPaths, List<PathMapping> catalog) {
    publicPaths.forEach(publicPath -> requireNotCovering(publicPath, catalog));
  }

  private static void requireNotCovering(String publicPath, List<PathMapping> catalog) {
    PathPattern pattern = PathPatternParser.defaultInstance.parse(publicPath);
    catalog.stream()
        .filter(mapping -> mapping.enforcementMode() != EnforcementMode.DISABLED)
        .map(PathMapping::path)
        .filter(path -> pattern.matches(PathContainer.parsePath(path)))
        .findFirst()
        .ifPresent(path -> fail(publicPath, path));
  }

  private static void fail(String publicPath, String catalogued) {
    throw new IllegalStateException(
        ("codehunters.security.public-paths entry %s covers the catalogued path %s"
                + " (codehunters.security.authz.paths): anonymous requests there are never"
                + " enforced. Remove the path from one of the two lists.")
            .formatted(publicPath, catalogued));
  }
}
