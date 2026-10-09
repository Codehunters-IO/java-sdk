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
import org.springframework.boot.context.properties.source.ConfigurationPropertyState;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Keeps renamed configuration keys working for one major version: every declared old key, and every
 * entry nested under it (list items, fields of list items), is copied to its new name, unless the
 * new name or any entry under it is already set, into a lowest-precedence property source. Like
 * Boot's binder, a list or map is copied whole from the highest-precedence source defining any of
 * its entries, never merged across sources. The most specific mapping wins, so single keys can move
 * to a different place than the rest of their prefix. One warning lists the old keys copied and
 * another the old keys ignored because the new key is set (never their values).
 *
 * <p>Declared keys are looked up the way Boot binds them, so an environment variable may write a
 * dashed name with or without underscores ({@code OLD_PREFIX_TIMEOUT_SECONDS} or {@code
 * OLD_PREFIX_TIMEOUTSECONDS}). Old keys that are not declared are not copied. Every declared key
 * must have a rename, which the constructor checks.
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
   * @throws IllegalArgumentException if a key has no rename
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
    this.keys.forEach(this::requireRename);
  }

  /**
   * Adds the aliases found in the environment.
   *
   * @param environment the environment to read and extend
   * @param log where the deprecation warnings go
   */
  public void apply(ConfigurableEnvironment environment, Log log) {
    Found found = new Found(environment, sourceName());
    keys.forEach(key -> copyKey(key, found));
    if (!found.ignored.isEmpty()) {
      log.warn("Deprecated configuration keys ignored, the new key is set: " + found.ignored);
    }
    if (found.aliases.isEmpty()) {
      return;
    }
    environment.getPropertySources().addLast(new MapPropertySource(sourceName(), found.aliases));
    log.warn("Deprecated configuration keys, rename them: " + found.renamed);
  }

  private String sourceName() {
    return "legacyAliases-" + name;
  }

  // Like Boot's binder, the whole value of a key, list or map, comes from the first source defining
  // it.
  private void copyKey(ConfigurationPropertyName key, Found found) {
    found.sources.stream()
        .filter(source -> defines(source, key))
        .findFirst()
        .ifPresent(source -> copyFrom(source, key, found));
  }

  private void copyFrom(
      ConfigurationPropertySource source, ConfigurationPropertyName key, Found found) {
    String newKey = newName(key);
    if (found.isDefined(ConfigurationPropertyName.of(newKey))) {
      found.ignored.put(key.toString(), newKey);
      return;
    }
    Optional.ofNullable(source.getConfigurationProperty(key))
        .ifPresent(property -> found.add(key, newKey, value(key, property)));
    copyIndexed(source, key, found);
    copyNested(source, key, found);
  }

  // A forward lookup lets Boot's environment-variable mapping match every accepted form.
  private void copyIndexed(
      ConfigurationPropertySource source, ConfigurationPropertyName key, Found found) {
    ConfigurationProperty item = source.getConfigurationProperty(indexed(key, 0));
    for (int index = 0;
        item != null;
        item = source.getConfigurationProperty(indexed(key, ++index))) {
      ConfigurationPropertyName itemName = indexed(key, index);
      found.add(itemName, newName(itemName), item.getValue());
    }
  }

  // Covers what a forward lookup cannot enumerate: nested fields such as paths[0].methods[0].
  private void copyNested(
      ConfigurationPropertySource source, ConfigurationPropertyName key, Found found) {
    if (!(source instanceof IterableConfigurationPropertySource iterable)) {
      return;
    }
    iterable.stream()
        .filter(key::isAncestorOf)
        .forEach(
            oldName ->
                found.add(
                    oldName,
                    newName(oldName),
                    source.getConfigurationProperty(oldName).getValue()));
  }

  private static boolean defines(
      ConfigurationPropertySource source, ConfigurationPropertyName key) {
    return source.getConfigurationProperty(key) != null
        || source.containsDescendantOf(key) == ConfigurationPropertyState.PRESENT;
  }

  private static ConfigurationPropertyName indexed(ConfigurationPropertyName key, int index) {
    return ConfigurationPropertyName.of(key + "[" + index + "]");
  }

  private void requireRename(ConfigurationPropertyName key) {
    if (findRename(key).isEmpty()) {
      throw new IllegalArgumentException("No rename declared for " + key);
    }
  }

  // Never empty for a key or its descendants: the constructor checked every key has a rename.
  private String newName(ConfigurationPropertyName oldName) {
    return findRename(oldName).orElseThrow();
  }

  private Optional<String> findRename(ConfigurationPropertyName oldName) {
    return renames.stream()
        .filter(rename -> rename.getKey().equals(oldName) || rename.getKey().isAncestorOf(oldName))
        .findFirst()
        .map(rename -> rename(oldName, rename.getKey(), rename.getValue()));
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

    // A second apply (parent and child contexts, a DevTools restart) must not read its own aliases
    // as new keys already set.
    private Found(ConfigurableEnvironment environment, String ownSource) {
      this.sources =
          StreamSupport.stream(ConfigurationPropertySources.get(environment).spliterator(), false)
              .filter(source -> !isOwn(source, ownSource))
              .toList();
    }

    private static boolean isOwn(ConfigurationPropertySource source, String ownSource) {
      return source.getUnderlyingSource() instanceof PropertySource<?> propertySource
          && propertySource.getName().equals(ownSource);
    }

    private boolean isDefined(ConfigurationPropertyName key) {
      return sources.stream().anyMatch(source -> defines(source, key));
    }

    private void add(ConfigurationPropertyName oldName, String newName, Object value) {
      aliases.put(newName, value);
      renamed.put(oldName.toString(), newName);
    }
  }
}
