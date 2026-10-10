package io.codehunters.parameter.config;

import io.codehunters.parameter.ParameterClient;
import io.codehunters.parameter.cache.CachingParameterClient;
import io.codehunters.parameter.cache.ParameterCache;
import io.codehunters.parameter.cache.ParameterCacheMetrics;
import io.codehunters.parameter.jdbc.JdbcParameterAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.ClassUtils;

/**
 * Auto-configures the parameter SDK against the application's {@link DataSource}. Active when
 * {@link JdbcClient} is on the classpath, there is a single {@link DataSource} candidate and {@code
 * codehunters.parameter.enabled} is not {@code false}.
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass(JdbcClient.class)
@ConditionalOnBooleanProperty(name = "codehunters.parameter.enabled", matchIfMissing = true)
@ConditionalOnSingleCandidate(DataSource.class)
@EnableConfigurationProperties(ParameterProperties.class)
public class ParameterAutoConfiguration {

  private static final String JDBC_CLIENT = "parameterJdbcClient";
  private static final String CACHE_METRICS =
      "io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics";

  /** Default constructor invoked by the Spring container. */
  public ParameterAutoConfiguration() {}

  /**
   * Builds the {@link JdbcClient} used to query the parameters table. It is not an autowiring
   * candidate, so it never competes with the application's own {@link JdbcClient}.
   *
   * @param dataSource the application's data source
   * @return the configured client
   */
  @Bean(name = JDBC_CLIENT, defaultCandidate = false)
  @ConditionalOnMissingBean(name = JDBC_CLIENT)
  public JdbcClient parameterJdbcClient(DataSource dataSource) {
    return JdbcClient.create(dataSource);
  }

  /**
   * Builds the {@link ParameterCache}: enabled when {@code cache-ttl} is positive, disabled (and
   * safe to invalidate) otherwise. Its metrics are bound when Micrometer is on the classpath and a
   * single {@link MeterRegistry} exists.
   *
   * @param properties the parameter configuration
   * @param resourceLoader supplies the application's class loader
   * @param beanFactory looked up for a meter registry, only when Micrometer is present
   * @return the cache
   */
  @Bean
  @ConditionalOnMissingBean(ParameterCache.class)
  public ParameterCache parameterCache(
      ParameterProperties properties, ResourceLoader resourceLoader, BeanFactory beanFactory) {
    ParameterCache cache = cache(properties);
    if (cache.isEnabled() && ClassUtils.isPresent(CACHE_METRICS, resourceLoader.getClassLoader())) {
      Metering.bind(cache, beanFactory);
    }
    return cache;
  }

  /**
   * Builds the default {@link ParameterClient}, cached when the {@link ParameterCache} is enabled.
   *
   * @param jdbcClient the JDBC client targeting the application's data source
   * @param properties the parameter configuration
   * @param cache where answers are kept
   * @return the JDBC-backed client
   */
  @Bean
  @ConditionalOnMissingBean(ParameterClient.class)
  public ParameterClient parameterClient(
      @Qualifier(JDBC_CLIENT) JdbcClient jdbcClient,
      ParameterProperties properties,
      ParameterCache cache) {
    var jdbc = new JdbcParameterAdapter(jdbcClient, properties.tableName());
    return cache.isEnabled() ? new CachingParameterClient(jdbc, cache) : jdbc;
  }

  private static ParameterCache cache(ParameterProperties properties) {
    return properties.isCacheEnabled()
        ? ParameterCache.create(properties.cacheTtl(), properties.cacheMaxEntries())
        : ParameterCache.disabled();
  }

  // Holds every Micrometer reference, so the class is loaded only when Micrometer is present.
  private static final class Metering {

    private Metering() {}

    static void bind(ParameterCache cache, BeanFactory beanFactory) {
      MeterRegistry meters = beanFactory.getBeanProvider(MeterRegistry.class).getIfUnique();
      if (meters != null) {
        new ParameterCacheMetrics(cache).bindTo(meters);
      }
    }
  }
}
