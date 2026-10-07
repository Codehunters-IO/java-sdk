package io.codehunters.parameter.config;

import io.codehunters.parameter.infrastructure.JdbcParameterAdapter;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the parameter SDK, bound from the {@code codehunters.parameter} prefix.
 *
 * @param enabled whether the SDK auto-configuration is active
 * @param tableName the parameters table, optionally schema-qualified ({@code schema.table})
 * @param cacheTtl how long answers are cached; {@code 0} disables the cache
 */
@ConfigurationProperties(prefix = "codehunters.parameter")
public record ParameterProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue(JdbcParameterAdapter.DEFAULT_TABLE) String tableName,
    @DefaultValue("0s") Duration cacheTtl) {

  /**
   * Validates the cache TTL; the table name is validated by {@link JdbcParameterAdapter}.
   *
   * @throws IllegalArgumentException if {@code cacheTtl} is negative
   */
  public ParameterProperties {
    if (cacheTtl.isNegative()) {
      throw new IllegalArgumentException(
          "codehunters.parameter.cache-ttl must not be negative: " + cacheTtl);
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
