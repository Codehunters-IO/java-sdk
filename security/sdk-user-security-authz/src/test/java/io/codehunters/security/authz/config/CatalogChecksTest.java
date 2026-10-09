package io.codehunters.security.authz.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties.EnforcementMode;
import io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties.PathMapping;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogChecksTest {

  private static final String PUBLIC_PATHS = "codehunters.security.public-paths";
  private static final String SKIP_PATHS = "codehunters.security.authz.skip-paths";
  private static final List<PathMapping> ORDERS = catalog("/api/orders/{id}", "ENFORCING");

  @Test
  void should_fail_when_a_literal_path_lies_under_a_catalogued_template() {
    assertThatThrownBy(() -> check(List.of("/api/orders/export"), ORDERS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("/api/orders/export")
        .hasMessageContaining("/api/orders/{id}");
  }

  @Test
  void should_fail_when_a_path_constrains_the_variable_of_a_catalogued_template() {
    assertThatThrownBy(() -> check(List.of("/api/orders/{id:[0-9]+}"), ORDERS))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_fail_when_a_path_names_an_instance_of_a_catalogued_template() {
    assertThatThrownBy(() -> check(List.of("/api/orders/1"), ORDERS))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_fail_when_a_path_lies_under_a_catalogued_prefix_wildcard() {
    assertThatThrownBy(() -> check(List.of("/api/orders/**"), catalog("/api/*", "ENFORCING")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_fail_when_a_path_covers_a_catalogued_path() {
    assertThatThrownBy(() -> check(List.of("/api/**"), catalog("/api/users/*", "ENFORCING")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_fail_when_a_capture_all_path_covers_a_catalogued_path() {
    assertThatThrownBy(() -> check(List.of("/api/{*rest}"), ORDERS))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_fail_when_a_catalogued_path_mapped_as_permissive_overlaps() {
    assertThatThrownBy(
            () -> check(List.of("/api/orders/1"), catalog("/api/orders/{id}", "PERMISSIVE")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_accept_paths_whose_literal_segments_differ() {
    assertThatCode(() -> check(List.of("/public/**", "/api/users/1"), ORDERS))
        .doesNotThrowAnyException();
  }

  @Test
  void should_accept_paths_with_a_different_number_of_segments() {
    assertThatCode(() -> check(List.of("/api/orders", "/api/orders/{id}/public"), ORDERS))
        .doesNotThrowAnyException();
  }

  @Test
  void should_ignore_catalogued_paths_mapped_as_disabled() {
    assertThatCode(() -> check(List.of("/api/orders/1"), catalog("/api/orders/{id}", "DISABLED")))
        .doesNotThrowAnyException();
  }

  @Test
  void should_name_the_property_of_the_overlapping_path() {
    assertThatThrownBy(
            () -> CatalogChecks.requireNoOverlap(SKIP_PATHS, List.of("/api/orders/1"), ORDERS))
        .hasMessageContaining(SKIP_PATHS);
  }

  private static void check(List<String> paths, List<PathMapping> catalog) {
    CatalogChecks.requireNoOverlap(PUBLIC_PATHS, paths, catalog);
  }

  private static List<PathMapping> catalog(String path, String mode) {
    return List.of(
        new PathMapping(
            path, "resource", EnforcementMode.valueOf(mode), List.of(), List.of(), Map.of()));
  }
}
