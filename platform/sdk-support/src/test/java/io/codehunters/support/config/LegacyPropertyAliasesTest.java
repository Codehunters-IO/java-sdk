package io.codehunters.support.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.impl.NoOpLog;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class LegacyPropertyAliasesTest {

  private final LegacyPropertyAliases aliases =
      new LegacyPropertyAliases(
          "test",
          Map.of("old.prefix.timeout-seconds", "new.prefix.timeout", "old.prefix", "new.prefix"),
          Map.of("old.prefix.timeout-seconds", seconds -> seconds + "s"),
          List.of(
              "old.prefix.url",
              "old.prefix.secret",
              "old.prefix.timeout-seconds",
              "old.prefix.client-secret",
              "old.prefix.paths",
              "old.prefix.allowed-origins",
              "old.prefix.rules"));

  private final StandardEnvironment environment = new StandardEnvironment();
  private final List<String> warnings = new ArrayList<>();

  @Test
  void should_copy_old_key_to_new_key_when_new_key_is_absent() {
    use(Map.of("old.prefix.url", "https://kc"));

    aliases.apply(environment, recordingLog());

    assertThat(environment.getProperty("new.prefix.url")).isEqualTo("https://kc");
  }

  @Test
  void should_keep_new_key_when_both_are_set() {
    use(Map.of("old.prefix.url", "https://old", "new.prefix.url", "https://new"));

    aliases.apply(environment, recordingLog());

    assertThat(environment.getProperty("new.prefix.url")).isEqualTo("https://new");
  }

  @Test
  void should_map_specific_key_before_generic_prefix() {
    use(Map.of("old.prefix.timeout-seconds", "30"));

    aliases.apply(environment, recordingLog());

    assertThat(environment.getProperty("new.prefix.timeout")).isEqualTo("30s");
    assertThat(environment.getProperty("new.prefix.timeout-seconds")).isNull();
  }

  @Test
  void should_keep_list_indexes_when_copying() {
    use(Map.of("old.prefix.paths[0]", "/a", "old.prefix.paths[1]", "/b"));

    aliases.apply(environment, recordingLog());

    assertThat(environment.getProperty("new.prefix.paths[1]")).isEqualTo("/b");
  }

  @Test
  void should_map_environment_variables_when_written_in_upper_case() {
    // Boot maps OLD_PREFIX_URL to old.prefix.url only for the source named "systemEnvironment".
    environment
        .getPropertySources()
        .replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("OLD_PREFIX_URL", "https://kc")));

    aliases.apply(environment, recordingLog());

    assertThat(environment.getProperty("new.prefix.url")).isEqualTo("https://kc");
  }

  @Test
  void should_map_dashed_key_and_transform_value_when_set_as_environment_variable() {
    environment
        .getPropertySources()
        .replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("OLD_PREFIX_TIMEOUTSECONDS", "30")));

    aliases.apply(environment, recordingLog());

    assertThat(environment.getProperty("new.prefix.timeout")).isEqualTo("30s");
  }

  @Test
  void should_map_environment_variable_that_splits_a_dashed_key_with_underscores() {
    useEnvironment(
        Map.of("OLD_PREFIX_CLIENT_SECRET", "s3cr3t", "OLD_PREFIX_TIMEOUT_SECONDS", "30"));

    aliases.apply(environment, recordingLog());

    assertThat(environment.getProperty("new.prefix.client-secret")).isEqualTo("s3cr3t");
    assertThat(environment.getProperty("new.prefix.timeout")).isEqualTo("30s");
  }

  @Test
  void should_map_indexed_environment_variables_of_a_dashed_list() {
    useEnvironment(
        Map.of(
            "OLD_PREFIX_ALLOWED_ORIGINS_0",
            "https://a",
            "OLD_PREFIX_ALLOWED_ORIGINS_1",
            "https://b"));

    aliases.apply(environment, recordingLog());

    assertThat(environment.getProperty("new.prefix.allowed-origins[0]")).isEqualTo("https://a");
    assertThat(environment.getProperty("new.prefix.allowed-origins[1]")).isEqualTo("https://b");
  }

  @Test
  void should_copy_nested_entries_of_a_declared_list_of_objects() {
    use(Map.of("old.prefix.rules[0].path", "/api/**"));

    aliases.apply(environment, recordingLog());

    assertThat(environment.getProperty("new.prefix.rules[0].path")).isEqualTo("/api/**");
  }

  @Test
  void should_not_copy_old_keys_that_are_not_declared() {
    use(Map.of("old.prefix.unknown", "x"));

    aliases.apply(environment, recordingLog());

    assertThat(environment.getProperty("new.prefix.unknown")).isNull();
  }

  @Test
  void should_warn_about_old_keys_ignored_because_the_new_key_is_set() {
    use(Map.of("old.prefix.url", "https://old", "new.prefix.url", "https://new"));

    aliases.apply(environment, recordingLog());

    assertThat(warnings).singleElement().asString().contains("ignored", "old.prefix.url");
  }

  @Test
  void should_warn_once_with_keys_but_without_values() {
    use(Map.of("old.prefix.secret", "s3cr3t", "old.prefix.url", "https://kc"));

    aliases.apply(environment, recordingLog());

    assertThat(warnings).hasSize(1);
    assertThat(warnings.getFirst()).contains("old.prefix.secret", "old.prefix.url");
    assertThat(warnings.getFirst()).doesNotContain("s3cr3t");
  }

  @Test
  void should_do_nothing_when_no_old_key_is_set() {
    use(Map.of("new.prefix.url", "https://kc"));

    aliases.apply(environment, recordingLog());

    assertThat(warnings).isEmpty();
    assertThat(environment.getPropertySources().contains("legacyAliases-test")).isFalse();
  }

  private void useEnvironment(Map<String, Object> variables) {
    // Boot maps OLD_PREFIX_URL to old.prefix.url only for the source named "systemEnvironment".
    environment
        .getPropertySources()
        .replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
  }

  private void use(Map<String, Object> properties) {
    environment.getPropertySources().addFirst(new MapPropertySource("application", properties));
  }

  private Log recordingLog() {
    return new NoOpLog() {
      @Override
      public void warn(Object message) {
        warnings.add(String.valueOf(message));
      }
    };
  }
}
