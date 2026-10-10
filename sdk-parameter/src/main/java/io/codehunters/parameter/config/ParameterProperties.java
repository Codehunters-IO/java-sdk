package io.codehunters.parameter.config;

import io.codehunters.parameter.cache.ParameterCache;
import io.codehunters.parameter.jdbc.JdbcParameterAdapter;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the parameter SDK, bound from the {@code codehunters.parameter} prefix.
 *
 * @param enabled whether the SDK auto-configuration is active
 * @param tableName the parameters table, optionally schema-qualified ({@code schema.table})
 * @param cacheTtl how long answers are cached; {@code 0} (the default) disables the cache
 * @param cacheMaxEntries the maximum number of cached answers, used when the cache is enabled
 */
@ConfigurationProperties(prefix = "codehunters.parameter")
public record ParameterProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue(JdbcParameterAdapter.DEFAULT_TABLE) String tableName,
    @DefaultValue("0s") Duration cacheTtl,
    @DefaultValue("" + ParameterCache.DEFAULT_MAX_ENTRIES) int cacheMaxEntries) {

  /**
   * Validates the cache settings; the table name is validated by {@link JdbcParameterAdapter}.
   *
   * @throws IllegalArgumentException if {@code cacheTtl} is negative or {@code cacheMaxEntries} is
   *     below one
   */
  public ParameterProperties {
    if (cacheTtl.isNegative()) {
      throw new IllegalArgumentException(
          "codehunters.parameter.cache-ttl must not be negative: " + cacheTtl);
    }
    if (cacheMaxEntries < 1) {
      throw new IllegalArgumentException(
          "codehunters.parameter.cache-max-entries must be positive: " + cacheMaxEntries);
    }
  }

  /**
   * Returns whether answers are cached.
   *
   * @return {@code true} when {@code cacheTtl} is positive
   */
  public boolean isCacheEnabled() {
    return !cacheTtl.isZero();
  }
}
