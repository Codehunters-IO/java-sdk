package io.codehunters.geo.postgis;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One PostGIS container, loaded with the zone fixture, for the whole test JVM; Testcontainers stops
 * it on exit. The image is a multi-arch build of PostGIS, so it runs natively on arm64.
 */
public final class PostgisDatabase {

  private static final DockerImageName IMAGE =
      DockerImageName.parse("imresamu/postgis:17-3.5-alpine").asCompatibleSubstituteFor("postgres");

  private static final PostgreSQLContainer CONTAINER = start();
  private static final DataSource DATA_SOURCE = loadFixture();

  private PostgisDatabase() {}

  /**
   * The database, with the {@code geo.zones} fixture loaded.
   *
   * @return a data source on the container
   */
  public static DataSource dataSource() {
    return DATA_SOURCE;
  }

  /**
   * The container's JDBC URL.
   *
   * @return the URL
   */
  public static String jdbcUrl() {
    return CONTAINER.getJdbcUrl();
  }

  private static PostgreSQLContainer start() {
    var container = new PostgreSQLContainer(IMAGE);
    container.start();
    return container;
  }

  private static DataSource loadFixture() {
    DataSource dataSource =
        new DriverManagerDataSource(
            CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
    JdbcClient.create(dataSource).sql(fixture()).update();
    return dataSource;
  }

  private static String fixture() {
    try (InputStream in =
        PostgisDatabase.class.getResourceAsStream("/postgis/geo-zone-fixture.sql")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
