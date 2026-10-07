package io.codehunters.parameter.infrastructure;

import io.codehunters.parameter.application.ParameterClient;
import io.codehunters.parameter.domain.exception.ParameterAccessException;
import io.codehunters.parameter.domain.exception.ParameterException;
import io.codehunters.parameter.domain.model.Parameter;
import io.codehunters.parameter.domain.model.ParameterModule;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link ParameterClient} implementation backed by direct JDBC reads against the parameters table.
 * Queries return only enabled, non-deleted rows. A blank submodule is treated as the empty
 * submodule.
 */
public class JdbcParameterAdapter implements ParameterClient {

  /** The table read when none is configured. */
  public static final String DEFAULT_TABLE = "parameters";

  private static final Pattern IDENTIFIER =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");
  private static final ParameterRowMapper ROW_MAPPER = new ParameterRowMapper();
  private static final String BASE_COLUMNS =
      "id, module, submodule, name, value, type, enabled, description, "
          + "created_by, created_at, updated_by, updated_at";
  private static final String ENABLED_FILTER = " AND enabled = :enabled AND deleted_at IS NULL";

  private final JdbcClient jdbcClient;
  private final String select;

  /**
   * Creates the adapter over the {@value #DEFAULT_TABLE} table.
   *
   * @param jdbcClient the JDBC client bound to the consumer's {@code DataSource}
   */
  public JdbcParameterAdapter(JdbcClient jdbcClient) {
    this(jdbcClient, DEFAULT_TABLE);
  }

  /**
   * Creates the adapter over the given table.
   *
   * @param jdbcClient the JDBC client bound to the consumer's {@code DataSource}
   * @param tableName the table to read, optionally schema-qualified ({@code schema.table})
   * @throws IllegalArgumentException if {@code tableName} is not a plain SQL identifier
   */
  public JdbcParameterAdapter(JdbcClient jdbcClient, String tableName) {
    if (tableName == null || !IDENTIFIER.matcher(tableName).matches()) {
      throw new IllegalArgumentException(
          "Parameter table name must be an identifier, optionally schema-qualified: " + tableName);
    }
    this.jdbcClient = jdbcClient;
    this.select = "SELECT " + BASE_COLUMNS + " FROM " + tableName;
  }

  @Override
  public Optional<Parameter> lookup(ParameterModule module, String submodule, String name) {
    String sql =
        select
            + " WHERE module = :module AND submodule = :submodule AND name = :name"
            + ENABLED_FILTER;
    Map<String, Object> params = baseParams(module);
    params.put("submodule", submodule == null ? "" : normalize(submodule));
    params.put("name", name);
    return execute(() -> jdbcClient.sql(sql).params(params).query(ROW_MAPPER).optional(), "lookup");
  }

  @Override
  public List<Parameter> findByModule(ParameterModule module, String submodule) {
    Map<String, Object> params = baseParams(module);
    String submoduleFilter = "";
    if (submodule != null) {
      submoduleFilter = " AND submodule = :submodule";
      params.put("submodule", normalize(submodule));
    }
    String sql =
        select + " WHERE module = :module" + submoduleFilter + ENABLED_FILTER + " ORDER BY name";
    return execute(
        () -> jdbcClient.sql(sql).params(params).query(ROW_MAPPER).list(), "findByModule");
  }

  @Override
  public List<Parameter> findByModules(Collection<ParameterModule> modules) {
    if (modules == null || modules.isEmpty()) {
      return List.of();
    }
    String sql = select + " WHERE module IN (:modules)" + ENABLED_FILTER + " ORDER BY module, name";
    Map<String, Object> params = new HashMap<>();
    params.put("modules", modules.stream().map(ParameterModule::code).toList());
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
        select
            + " WHERE module = :module AND submodule IN (:submodules)"
            + ENABLED_FILTER
            + " ORDER BY submodule, name";
    Map<String, Object> params = baseParams(module);
    params.put("submodules", submodules.stream().map(JdbcParameterAdapter::normalize).toList());
    return execute(
        () -> jdbcClient.sql(sql).params(params).query(ROW_MAPPER).list(),
        "findByModuleAndSubmodules");
  }

  /**
   * {@inheritDoc}
   *
   * <p>The map keeps the query order (by name) and may hold {@code null} values.
   *
   * @throws ParameterException if two returned rows share a name
   */
  @Override
  public Map<String, String> asMap(ParameterModule module, String submodule) {
    return byName(findByModule(module, submodule), Parameter::value);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The map keeps the query order (by name).
   *
   * @throws ParameterException if two returned rows share a name
   */
  @Override
  public Map<String, Parameter> asMapByName(ParameterModule module, String submodule) {
    return byName(findByModule(module, submodule), Function.identity());
  }

  private static <V> Map<String, V> byName(List<Parameter> rows, Function<Parameter, V> value) {
    Map<String, V> result = new LinkedHashMap<>();
    for (Parameter row : rows) {
      if (result.containsKey(row.name())) {
        throw new ParameterException(
            "Parameter name appears in more than one submodule; narrow by submodule: "
                + row.name());
      }
      result.put(row.name(), value.apply(row));
    }
    return Collections.unmodifiableMap(result);
  }

  private static Map<String, Object> baseParams(ParameterModule module) {
    Map<String, Object> params = new HashMap<>();
    params.put("module", module.code());
    params.put("enabled", Boolean.TRUE);
    return params;
  }

  private static String normalize(String submodule) {
    return submodule.isBlank() ? "" : submodule;
  }

  private <T> T execute(Supplier<T> call, String operation) {
    try {
      return call.get();
    } catch (DataAccessException ex) {
      throw new ParameterAccessException(
          "JDBC error querying parameters operation=" + operation, ex);
    }
  }
}
