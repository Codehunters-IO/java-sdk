package io.codehunters.geo.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.codehunters.geo.CoverageType;
import io.codehunters.geo.DenialReason;
import io.codehunters.geo.GeoPoint;
import io.codehunters.geo.GeoValidationDecision;
import io.codehunters.geo.Latitude;
import io.codehunters.geo.Longitude;
import io.codehunters.geo.MatchedZoneType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

class SqlServerGeoValidationAdapterTest {

  @Test
  void national_coverage_allows_without_touching_the_database() {
    JdbcClient jdbcClient = mock(JdbcClient.class);
    SqlServerGeoValidationAdapter adapter = new SqlServerGeoValidationAdapter(jdbcClient);
    GeoPoint point = new GeoPoint(new Latitude(4.7110), new Longitude(-74.0721));

    GeoValidationDecision decision =
        adapter.canPurchase(UUID.randomUUID(), point, CoverageType.NATIONAL);

    assertTrue(decision.allowed());
    assertNull(decision.zoneCode());
    assertNull(decision.zoneType());
    verifyNoInteractions(jdbcClient);
  }

  @Test
  @SuppressWarnings("unchecked")
  void departmental_coverage_queries_procedure_and_maps_row() throws SQLException {
    JdbcClient jdbcClient = mock(JdbcClient.class);
    JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class);
    JdbcClient.MappedQuerySpec<GeoValidationDecision> query =
        mock(JdbcClient.MappedQuerySpec.class);
    when(jdbcClient.sql(anyString())).thenReturn(spec);
    when(spec.param(anyString(), any())).thenReturn(spec);
    when(spec.query(any(RowMapper.class))).thenReturn(query);
    when(query.single()).thenReturn(GeoValidationDecision.denied());

    SqlServerGeoValidationAdapter adapter = new SqlServerGeoValidationAdapter(jdbcClient);
    GeoPoint point = new GeoPoint(new Latitude(6.2518), new Longitude(-75.5636));

    GeoValidationDecision decision =
        adapter.canPurchase(UUID.randomUUID(), point, CoverageType.DEPARTMENTAL);

    assertFalse(decision.allowed());

    ArgumentCaptor<RowMapper<GeoValidationDecision>> captor =
        ArgumentCaptor.forClass(RowMapper.class);
    verify(spec).query(captor.capture());
    ResultSet rs = mock(ResultSet.class);
    when(rs.getBoolean("allowed")).thenReturn(true);
    when(rs.getString("code")).thenReturn("05001");
    when(rs.getString("type")).thenReturn("MUNICIPALITY");
    when(rs.getString("name")).thenReturn("Medellin");
    when(rs.getString("dept")).thenReturn("Antioquia");
    assertTrue(captor.getValue().mapRow(rs, 0).allowed());
  }

  @Test
  void allowed_row_maps_to_allowing_decision() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getBoolean("allowed")).thenReturn(true);
    when(rs.getString("code")).thenReturn("05001");
    when(rs.getString("type")).thenReturn("MUNICIPALITY");
    when(rs.getString("name")).thenReturn("Medellin");
    when(rs.getString("dept")).thenReturn("Antioquia");

    GeoValidationDecision decision = SqlServerGeoValidationAdapter.mapRow(rs);

    assertTrue(decision.allowed());
    assertEquals("05001", decision.zoneCode());
    assertEquals(MatchedZoneType.MUNICIPALITY, decision.zoneType());
    assertEquals("Medellin", decision.zoneName());
    assertEquals("Antioquia", decision.department());
  }

  @Test
  void denied_row_maps_to_denying_decision() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getBoolean("allowed")).thenReturn(false);

    GeoValidationDecision decision = SqlServerGeoValidationAdapter.mapRow(rs);

    assertFalse(decision.allowed());
    assertEquals(DenialReason.OUTSIDE_ALLOWED_ZONE, decision.denialReason());
  }

  @Test
  void allowed_flag_without_matched_code_maps_to_denying_decision() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getBoolean("allowed")).thenReturn(true);
    when(rs.getString("code")).thenReturn(null);

    GeoValidationDecision decision = SqlServerGeoValidationAdapter.mapRow(rs);

    assertFalse(decision.allowed());
    assertEquals(DenialReason.OUTSIDE_ALLOWED_ZONE, decision.denialReason());
  }
}
