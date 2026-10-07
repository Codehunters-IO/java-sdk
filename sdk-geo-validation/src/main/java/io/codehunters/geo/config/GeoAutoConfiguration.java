package io.codehunters.geo.config;

import io.codehunters.geo.GeoFenceValidator;
import io.codehunters.geo.polygon.GeoZoneSource;
import io.codehunters.geo.polygon.PolygonGeoFence;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

/**
 * Registers the geofence selected by {@code codehunters.geo.adapter}: {@code polygon} over the
 * application's single {@link GeoZoneSource}, or {@code sql-server} over the configured procedure
 * and the single (or primary) {@link DataSource}. No adapter, an application-defined {@link
 * GeoFenceValidator} or {@code codehunters.geo.enabled=false} registers nothing. A selected adapter
 * whose dependencies are missing fails at startup.
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnBooleanProperty(name = "codehunters.geo.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GeoProperties.class)
public class GeoAutoConfiguration {

  private static final String JDBC_CLIENT = "org.springframework.jdbc.core.simple.JdbcClient";

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public GeoAutoConfiguration() {}

  /**
   * The geofence of the selected adapter.
   *
   * @param properties the SDK configuration
   * @param zoneSources the application's zone source, used by {@code polygon}
   * @param dataSources the application's data source, used by {@code sql-server}
   * @param resources where the application's classes are loaded from
   * @return the geofence
   * @throws IllegalStateException if the selected adapter's dependencies are missing
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "codehunters.geo.adapter")
  public GeoFenceValidator geoFenceValidator(
      GeoProperties properties,
      ObjectProvider<GeoZoneSource> zoneSources,
      ObjectProvider<DataSource> dataSources,
      ResourceLoader resources) {
    return switch (properties.adapter()) {
      case POLYGON -> new PolygonGeoFence(zoneSource(zoneSources));
      case SQL_SERVER -> sqlServer(properties, dataSources, resources.getClassLoader());
    };
  }

  private static GeoZoneSource zoneSource(ObjectProvider<GeoZoneSource> zoneSources) {
    GeoZoneSource source = zoneSources.getIfUnique();
    if (source == null) {
      throw new IllegalStateException(
          "codehunters.geo.adapter=polygon needs exactly one GeoZoneSource bean");
    }
    return source;
  }

  private static GeoFenceValidator sqlServer(
      GeoProperties properties, ObjectProvider<DataSource> dataSources, ClassLoader classLoader) {
    if (!ClassUtils.isPresent(JDBC_CLIENT, classLoader)) {
      throw new IllegalStateException(
          "codehunters.geo.adapter=sql-server needs spring-jdbc on the classpath");
    }
    DataSource dataSource = dataSources.getIfUnique();
    if (dataSource == null) {
      throw new IllegalStateException(
          "codehunters.geo.adapter=sql-server needs exactly one DataSource bean, or a primary one");
    }
    return SqlServerGeoFences.create(dataSource, properties.sqlServer().procedure());
  }
}
