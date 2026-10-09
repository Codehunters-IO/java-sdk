package io.codehunters.parameter.jdbc;

import io.codehunters.parameter.Parameter;
import io.codehunters.parameter.ParameterAccessException;
import io.codehunters.parameter.ParameterModule;
import io.codehunters.parameter.ParameterType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps a {@code parameters} table row into a {@link Parameter}. Any module code is accepted; an
 * unknown {@code type} is logged and surfaced as {@code null} rather than failing the query.
 */
final class ParameterRowMapper implements RowMapper<Parameter> {

  private static final Logger log = LoggerFactory.getLogger(ParameterRowMapper.class);

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
    try {
      return UUID.fromString(raw.toString());
    } catch (IllegalArgumentException ex) {
      throw new ParameterAccessException("Malformed UUID in column " + column, null);
    }
  }

  private OffsetDateTime readTimestamp(ResultSet rs, String column) throws SQLException {
    // JDBC 4.2 typed access: the driver converts TIMESTAMPTZ itself, with no string parsing.
    return rs.getObject(column, OffsetDateTime.class);
  }

  private ParameterModule parseModule(String value) {
    if (value == null || value.isBlank()) {
      log.warn("Parameter row without a module");
      return null;
    }
    return ParameterModule.of(value);
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
