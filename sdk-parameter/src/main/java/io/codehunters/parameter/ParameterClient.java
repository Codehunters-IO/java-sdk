package io.codehunters.parameter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only client for the shared parameters table. Implementations query the database directly
 * through JDBC; consumers must supply a {@link javax.sql.DataSource}.
 *
 * <p>All operations return only enabled, non-deleted rows. A blank submodule is treated as the
 * empty submodule.
 */
public interface ParameterClient {

  /**
   * Looks up a single parameter by its full key.
   *
   * @param module the owning module
   * @param submodule the submodule; {@code null} or blank matches rows stored with empty submodule
   * @param name the parameter name
   * @return the parameter when present, otherwise {@link Optional#empty()}
   */
  Optional<Parameter> lookup(ParameterModule module, String submodule, String name);

  /**
   * Returns all parameters for a module, optionally narrowed by submodule.
   *
   * @param module the owning module
   * @param submodule the submodule filter; {@code null} returns all submodules
   * @return the matching parameters in insertion order (by {@code name})
   */
  List<Parameter> findByModule(ParameterModule module, String submodule);

  /**
   * Returns all parameters whose module is in the supplied collection.
   *
   * @param modules the modules to include; an empty collection returns an empty list
   * @return the matching parameters, ordered by {@code module} then {@code name}
   * @throws IllegalArgumentException if {@code modules} contains a {@code null} element
   */
  List<Parameter> findByModules(Collection<ParameterModule> modules);

  /**
   * Returns all parameters for a module whose submodule is in the supplied collection.
   *
   * @param module the owning module
   * @param submodules the submodules to include; an empty collection returns an empty list
   * @return the matching parameters, ordered by {@code submodule} then {@code name}
   * @throws IllegalArgumentException if {@code submodules} contains a {@code null} element
   */
  List<Parameter> findByModuleAndSubmodules(ParameterModule module, Collection<String> submodules);

  /**
   * Convenience accessor returning a {@code name → value} map for a module/submodule pair.
   *
   * @param module the owning module
   * @param submodule the submodule filter; {@code null} returns all submodules
   * @return an unmodifiable map keyed by parameter name, in name order; values may be {@code null}
   * @throws io.codehunters.parameter.ParameterException if two rows share a name, which happens
   *     when {@code submodule} is {@code null} and a name exists in two submodules
   */
  Map<String, String> asMap(ParameterModule module, String submodule);

  /**
   * Convenience accessor returning a {@code name → Parameter} map for a module/submodule pair.
   *
   * @param module the owning module
   * @param submodule the submodule filter; {@code null} returns all submodules
   * @return an unmodifiable map keyed by parameter name, in name order
   * @throws io.codehunters.parameter.ParameterException if two rows share a name, which happens
   *     when {@code submodule} is {@code null} and a name exists in two submodules
   */
  Map<String, Parameter> asMapByName(ParameterModule module, String submodule);
}
