package io.codehunters.support.config;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.apache.commons.logging.Log;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Keeps renamed configuration keys working for one major version: every key under an old prefix is
 * copied to its new name, unless the new name is already set, into a lowest-precedence property
 * source. The most specific mapping wins, so single keys can move to a different place than the
 * rest of their prefix. One warning lists the old keys found (never their values).
 *
 * <p>Internal to the SDK modules; not a supported API.
 */
public final class LegacyPropertyAliases {

  private final String name;
  private final List<Map.Entry<ConfigurationPropertyName, String>> renames;
  private final List<Map.Entry<ConfigurationPropertyName, UnaryOperator<String>>> valueTransforms;

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
  }

  /**
   * Adds the aliases found in the environment.
   *
   * @param environment the environment to read and extend
   * @param log where the deprecation warning goes
   */
  public void apply(ConfigurableEnvironment environment, Log log) {
    Map<String, Object> aliases = new LinkedHashMap<>();
    Map<String, String> renamed = new LinkedHashMap<>();
    for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
      collect(source, environment, aliases, renamed);
    }
    if (aliases.isEmpty()) {
      return;
    }
    environment
        .getPropertySources()
        .addLast(new MapPropertySource("legacyAliases-" + name, aliases));
    log.warn("Deprecated configuration keys, rename them: " + renamed);
  }

  private void collect(
      ConfigurationPropertySource source,
      ConfigurableEnvironment environment,
      Map<String, Object> aliases,
      Map<String, String> renamed) {
    if (!(source instanceof IterableConfigurationPropertySource iterable)) {
      return;
    }
    iterable.stream()
        .forEach(
            oldName ->
                newName(oldName)
                    .filter(newName -> !aliases.containsKey(newName))
                    .filter(newName -> !isSet(environment, newName))
                    .ifPresent(
                        newName -> {
                          aliases.put(
                              newName, value(oldName, source.getConfigurationProperty(oldName)));
                          renamed.put(oldName.toString(), newName);
                        }));
  }

  // Names are compared element by element: an environment variable such as
  // KEYCLOAK_SECURITY_CLIENTSECRET is the same name as keycloak.security.client-secret.
  private Optional<String> newName(ConfigurationPropertyName oldName) {
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

  private static boolean isSet(ConfigurableEnvironment environment, String key) {
    ConfigurationPropertyName name = ConfigurationPropertyName.of(key);
    for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
      if (source.getConfigurationProperty(name) != null) {
        return true;
      }
    }
    return false;
  }
}
