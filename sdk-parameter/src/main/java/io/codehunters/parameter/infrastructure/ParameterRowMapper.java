package io.codehunters.parameter.infrastructure;

import io.codehunters.parameter.domain.model.Parameter;
import io.codehunters.parameter.domain.model.ParameterModule;
import io.codehunters.parameter.domain.model.ParameterType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps a {@code parameters} table row into a {@link Parameter}. Unknown {@code module} or {@code
 * type} enum values are logged and surfaced as {@code null} rather than failing the query.
 */
@Slf4j
final class ParameterRowMapper implements RowMapper<Parameter> {

  @Override
  public Parameter mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new Parameter(
        readUuid(rs, "id"),
        parseModule(rs.getString("module")),
        rs.getString("submodule"),
        rs.getString("name"),
        rs.getString("value"),
        parseType(rs.getString("type")),
        rs.getBoolean("enabled"),
        rs.getString("description"),
        readUuid(rs, "created_by"),
        readTimestamp(rs, "created_at"),
        readUuid(rs, "updated_by"),
        readTimestamp(rs, "updated_at"));
  }

  private UUID readUuid(ResultSet rs, String column) throws SQLException {
    Object raw = rs.getObject(column);
    if (raw == null) {
      return null;
    }
    if (raw instanceof UUID uuid) {
      return uuid;
    }
    return UUID.fromString(raw.toString());
  }

  private OffsetDateTime readTimestamp(ResultSet rs, String column) throws SQLException {
    // JDBC 4.2 typed access: the SQL Server driver returns DATETIMEOFFSET as
    // microsoft.sql.DateTimeOffset, whose toString() is not ISO-8601 and fails
    // OffsetDateTime.parse. Requesting OffsetDateTime lets the driver convert it.
    return rs.getObject(column, OffsetDateTime.class);
  }

  private ParameterModule parseModule(String value) {
    if (value == null) {
      return null;
    }
    try {
      return ParameterModule.valueOf(value);
    } catch (IllegalArgumentException ex) {
      log.warn("Unknown parameter module received: {}", value);
      return null;
    }
  }

  private ParameterType parseType(String value) {
    if (value == null) {
      return null;
    }
    try {
      return ParameterType.valueOf(value);
    } catch (IllegalArgumentException ex) {
      log.warn("Unknown parameter type received: {}", value);
      return null;
    }
  }
}
