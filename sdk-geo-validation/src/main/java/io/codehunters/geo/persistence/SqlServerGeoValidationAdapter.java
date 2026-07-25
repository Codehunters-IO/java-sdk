package io.codehunters.geo.persistence;

import io.codehunters.geo.CoverageType;
import io.codehunters.geo.GeoPoint;
import io.codehunters.geo.GeoValidationDecision;
import io.codehunters.geo.MatchedZoneType;
import io.codehunters.geo.RaffleGeoValidationPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * SQL Server geography-backed adapter. Calls {@code sp_geo_can_purchase_raffle} through a SELECT
 * wrapper that captures the procedure's OUTPUT parameters in a single row.
 */
public class SqlServerGeoValidationAdapter implements RaffleGeoValidationPort {

  private static final String SQL =
      """
      DECLARE @allowed BIT, @code VARCHAR(10), @type VARCHAR(20), @name NVARCHAR(200), @dept NVARCHAR(200);
      EXEC dbo.sp_geo_can_purchase_raffle
          @raffle_id = :raffleId, @lat = :lat, @lon = :lon,
          @allowed = @allowed OUTPUT, @matched_code = @code OUTPUT, @matched_type = @type OUTPUT,
          @matched_name = @name OUTPUT, @matched_department = @dept OUTPUT;
      SELECT @allowed AS allowed, @code AS code, @type AS type, @name AS name, @dept AS dept;
      """;

  private final JdbcClient jdbcClient;

  /**
   * Creates the adapter.
   *
   * @param jdbcClient JDBC client backed by a SQL Server {@code DataSource}
   */
  public SqlServerGeoValidationAdapter(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public GeoValidationDecision canPurchase(
      UUID raffleId, GeoPoint point, CoverageType coverageType) {
    if (coverageType == CoverageType.NATIONAL) {
      return GeoValidationDecision.allowed(null, null, null, null);
    }
    return jdbcClient
        .sql(SQL)
        .param("raffleId", raffleId)
        .param("lat", point.latitude().value())
        .param("lon", point.longitude().value())
        .query((rs, n) -> mapRow(rs))
        .single();
  }

  /**
   * Maps a single result row produced by {@code sp_geo_can_purchase_raffle} into a {@link
   * GeoValidationDecision}. A row is only treated as allowing when the procedure both raised the
   * {@code allowed} flag and returned a matched zone code; an allowed flag with no matched zone is
   * treated as denied, guarding against inconsistent procedure output.
   *
   * @param rs the result set positioned on the row to map
   * @return the mapped {@link GeoValidationDecision}
   * @throws SQLException if reading a column fails
   */
  static GeoValidationDecision mapRow(ResultSet rs) throws SQLException {
    boolean allowed = rs.getBoolean("allowed");
    String code = rs.getString("code");
    String type = rs.getString("type");
    if (allowed && code != null && type != null) {
      return GeoValidationDecision.allowed(
          code, rs.getString("name"), MatchedZoneType.valueOf(type), rs.getString("dept"));
    }
    return GeoValidationDecision.denied();
  }
}
