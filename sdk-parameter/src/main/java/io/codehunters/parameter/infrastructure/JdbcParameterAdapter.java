package io.codehunters.parameter.infrastructure;

import io.codehunters.parameter.application.ParameterClient;
import io.codehunters.parameter.domain.exception.ParameterAccessException;
import io.codehunters.parameter.domain.model.Parameter;
import io.codehunters.parameter.domain.model.ParameterModule;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link ParameterClient} implementation backed by direct JDBC reads against the {@code parameters}
 * table owned by {@code vitxo-ms-managment}. Queries return only enabled, non-deleted rows.
 */
@Slf4j
public class JdbcParameterAdapter implements ParameterClient {

  private static final String TABLE = "parameters";
  private static final ParameterRowMapper ROW_MAPPER = new ParameterRowMapper();
  private static final String BASE_COLUMNS =
      "id, module, submodule, name, value, type, enabled, description, "
          + "created_by, created_at, updated_by, updated_at";
  private static final String ENABLED_FILTER = " AND enabled = :enabled AND deleted_at IS NULL";

  private final JdbcClient jdbcClient;

  /**
   * Creates the adapter.
   *
   * @param jdbcClient the JDBC client bound to the consumer's {@code DataSource}
   */
  public JdbcParameterAdapter(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public Optional<Parameter> lookup(ParameterModule module, String submodule, String name) {
    String sql =
        "SELECT "
            + BASE_COLUMNS
            + " FROM "
            + TABLE
            + " WHERE module = :module AND submodule = :submodule AND name = :name"
            + ENABLED_FILTER;
    Map<String, Object> params = new HashMap<>();
    params.put("module", module.name());
    params.put("submodule", submodule == null ? "" : submodule);
    params.put("name", name);
    params.put("enabled", Boolean.TRUE);
    return execute(() -> jdbcClient.sql(sql).params(params).query(ROW_MAPPER).optional(), "lookup");
  }

  @Override
  public List<Parameter> findByModule(ParameterModule module, String submodule) {
    StringBuilder sql =
        new StringBuilder("SELECT ")
            .append(BASE_COLUMNS)
            .append(" FROM ")
            .append(TABLE)
            .append(" WHERE module = :module");
    Map<String, Object> params = new HashMap<>();
    params.put("module", module.name());
    if (submodule != null) {
      sql.append(" AND submodule = :submodule");
      params.put("submodule", submodule);
    }
    sql.append(ENABLED_FILTER).append(" ORDER BY name");
    params.put("enabled", Boolean.TRUE);
    String query = sql.toString();
    return execute(
        () -> jdbcClient.sql(query).params(params).query(ROW_MAPPER).list(), "findByModule");
  }

  @Override
  public List<Parameter> findByModules(Collection<ParameterModule> modules) {
    if (modules == null || modules.isEmpty()) {
      return List.of();
    }
    String sql =
        "SELECT "
            + BASE_COLUMNS
            + " FROM "
            + TABLE
            + " WHERE module IN (:modules)"
            + ENABLED_FILTER
            + " ORDER BY module, name";
    Map<String, Object> params = new HashMap<>();
    params.put("modules", modules.stream().map(ParameterModule::name).collect(Collectors.toList()));
    params.put("enabled", Boolean.TRUE);
    return execute(
        () -> jdbcClient.sql(sql).params(params).query(ROW_MAPPER).list(), "findByModules");
  }

  @Override
  public List<Parameter> findByModuleAndSubmodules(
      ParameterModule module, Collection<String> submodules) {
    if (submodules == null || submodules.isEmpty()) {
      return List.of();
    }
    String sql =
        "SELECT "
            + BASE_COLUMNS
            + " FROM "
            + TABLE
            + " WHERE module = :module AND submodule IN (:submodules)"
            + ENABLED_FILTER
            + " ORDER BY submodule, name";
    Map<String, Object> params = new HashMap<>();
    params.put("module", module.name());
    params.put("submodules", List.copyOf(submodules));
    params.put("enabled", Boolean.TRUE);
    return execute(
        () -> jdbcClient.sql(sql).params(params).query(ROW_MAPPER).list(),
        "findByModuleAndSubmodules");
  }

  @Override
  public Map<String, String> asMap(ParameterModule module, String submodule) {
    Map<String, String> result = new LinkedHashMap<>();
    for (Parameter p : findByModule(module, submodule)) {
      result.put(p.name(), p.value());
    }
    return Map.copyOf(result);
  }

  @Override
  public Map<String, Parameter> asMapByName(ParameterModule module, String submodule) {
    Map<String, Parameter> result = new LinkedHashMap<>();
    for (Parameter p : findByModule(module, submodule)) {
      result.put(p.name(), p);
    }
    return Map.copyOf(result);
  }

  private <T> T execute(java.util.function.Supplier<T> call, String operation) {
    try {
      return call.get();
    } catch (DataAccessException ex) {
      throw new ParameterAccessException(
          "JDBC error querying parameters operation=" + operation, ex);
    }
  }
}
