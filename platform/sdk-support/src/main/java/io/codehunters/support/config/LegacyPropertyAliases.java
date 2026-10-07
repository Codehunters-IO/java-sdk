package io.codehunters.support.config;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.StreamSupport;
import org.apache.commons.logging.Log;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Keeps renamed configuration keys working for one major version: every declared old key, and every
 * entry nested under it (list items, fields of list items), is copied to its new name, unless the
 * new name is already set, into a lowest-precedence property source. The most specific mapping
 * wins, so single keys can move to a different place than the rest of their prefix. One warning
 * lists the old keys copied and another the old keys ignored because the new key is set (never
 * their values).
 *
 * <p>Declared keys are looked up the way Boot binds them, so an environment variable may write a
 * dashed name with or without underscores ({@code OLD_PREFIX_TIMEOUT_SECONDS} or {@code
 * OLD_PREFIX_TIMEOUTSECONDS}). Old keys that are not declared are not copied.
 *
 * <p>Internal to the SDK modules; not a supported API.
 */
public final class LegacyPropertyAliases {

  private final String name;
  private final List<Map.Entry<ConfigurationPropertyName, String>> renames;
  private final List<Map.Entry<ConfigurationPropertyName, UnaryOperator<String>>> valueTransforms;
  private final List<ConfigurationPropertyName> keys;

  /**
   * Creates the aliases.
   *
   * @param name identifies the property source this instance adds
   * @param renames old key or prefix to new key or prefix
   * @param valueTransforms old key to a conversion applied to its value, e.g. seconds to a duration
   * @param keys every old key that is aliased, in canonical form
   */
  public LegacyPropertyAliases(
      String name,
      Map<String, String> renames,
      Map<String, UnaryOperator<String>> valueTransforms,
      Collection<String> keys) {
    this.name = name;
    this.renames =
        renames.entrySet().stream()
            .map(e -> Map.entry(ConfigurationPropertyName.of(e.getKey()), e.getValue()))
            .sorted(Comparator.comparingInt(e -> -e.getKey().getNumberOfElements()))
            .toList();
    this.valueTransforms =
        valueTransforms.entrySet().stream()
            .map(e -> Map.entry(ConfigurationPropertyName.of(e.getKey()), e.getValue()))
            .toList();
    this.keys = keys.stream().map(ConfigurationPropertyName::of).toList();
  }

  /**
   * Adds the aliases found in the environment.
   *
   * @param environment the environment to read and extend
   * @param log where the deprecation warnings go
   */
  public void apply(ConfigurableEnvironment environment, Log log) {
    Found found = new Found(environment);
    keys.forEach(key -> copyKey(key, found));
    found.sources.forEach(source -> copyNested(source, found));
    if (!found.ignored.isEmpty()) {
      log.warn("Deprecated configuration keys ignored, the new key is set: " + found.ignored);
    }
    if (found.aliases.isEmpty()) {
      return;
    }
    environment
        .getPropertySources()
        .addLast(new MapPropertySource("legacyAliases-" + name, found.aliases));
    log.warn("Deprecated configuration keys, rename them: " + found.renamed);
  }

  // A forward lookup lets Boot's environment-variable mapping match every accepted form.
  private void copyKey(ConfigurationPropertyName key, Found found) {
    found.lookup(key).ifPresent(property -> found.add(key, newName(key), value(key, property)));
    int index = 0;
    Optional<ConfigurationProperty> item = found.lookup(indexed(key, index));
    while (item.isPresent()) {
      ConfigurationPropertyName itemName = indexed(key, index);
      found.add(itemName, newName(itemName), item.get().getValue());
      item = found.lookup(indexed(key, ++index));
    }
  }

  // Covers what a forward lookup cannot enumerate: nested fields such as paths[0].methods[0].
  private void copyNested(ConfigurationPropertySource source, Found found) {
    if (!(source instanceof IterableConfigurationPropertySource iterable)) {
      return;
    }
    iterable.stream()
        .filter(oldName -> keys.stream().anyMatch(key -> key.isAncestorOf(oldName)))
        .forEach(
            oldName ->
                found.add(
                    oldName,
                    newName(oldName),
                    source.getConfigurationProperty(oldName).getValue()));
  }

  private static ConfigurationPropertyName indexed(ConfigurationPropertyName key, int index) {
    return ConfigurationPropertyName.of(key + "[" + index + "]");
  }

  private String newName(ConfigurationPropertyName oldName) {
    return renames.stream()
        .filter(rename -> rename.getKey().equals(oldName) || rename.getKey().isAncestorOf(oldName))
        .findFirst()
        .map(rename -> rename(oldName, rename.getKey(), rename.getValue()))
        .orElseThrow(() -> new IllegalStateException("No rename declared for " + oldName));
  }

  private static String rename(
      ConfigurationPropertyName oldName, ConfigurationPropertyName oldPrefix, String newPrefix) {
    ConfigurationPropertyName suffix = oldName.subName(oldPrefix.getNumberOfElements());
    return ConfigurationPropertyName.of(newPrefix).append(suffix).toString();
  }

  private Object value(ConfigurationPropertyName oldName, ConfigurationProperty property) {
    Object value = property.getValue();
    return valueTransforms.stream()
        .filter(transform -> transform.getKey().equals(oldName))
        .findFirst()
        .<Object>map(transform -> transform.getValue().apply(String.valueOf(value)))
        .orElse(value);
  }

  private static final class Found {

    private final List<ConfigurationPropertySource> sources;
    private final Map<String, Object> aliases = new LinkedHashMap<>();
    private final Map<String, String> renamed = new LinkedHashMap<>();
    private final Map<String, String> ignored = new LinkedHashMap<>();

    private Found(ConfigurableEnvironment environment) {
      this.sources =
          StreamSupport.stream(ConfigurationPropertySources.get(environment).spliterator(), false)
              .toList();
    }

    // Sources come in precedence order, so the first one holding the key wins.
    private Optional<ConfigurationProperty> lookup(ConfigurationPropertyName key) {
      return sources.stream()
          .map(source -> source.getConfigurationProperty(key))
          .filter(property -> property != null)
          .findFirst();
    }

    private void add(ConfigurationPropertyName oldName, String newName, Object value) {
      if (aliases.containsKey(newName) || ignored.containsKey(oldName.toString())) {
        return;
      }
      if (lookup(ConfigurationPropertyName.of(newName)).isPresent()) {
        ignored.put(oldName.toString(), newName);
        return;
      }
      aliases.put(newName, value);
      renamed.put(oldName.toString(), newName);
    }
  }
}
