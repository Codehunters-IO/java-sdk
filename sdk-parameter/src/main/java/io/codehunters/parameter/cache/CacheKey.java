package io.codehunters.parameter.cache;

import io.codehunters.parameter.ParameterModule;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Identifies one cached answer: the client operation, the module codes it reads, and its remaining
 * normalized arguments. The module codes let {@link ParameterCache#invalidate(ParameterModule)}
 * find every answer that depends on a module.
 *
 * @param operation the {@link io.codehunters.parameter.ParameterClient} method name
 * @param modules the codes of the modules the answer reads, sorted
 * @param arguments the other normalized arguments; may hold {@code null}
 */
record CacheKey(String operation, List<String> modules, List<Object> arguments) {

  static CacheKey of(String operation, ParameterModule module, Object... arguments) {
    List<String> modules = module == null ? List.of() : List.of(module.code());
    return new CacheKey(operation, modules, Collections.unmodifiableList(Arrays.asList(arguments)));
  }

  static CacheKey ofModules(String operation, List<String> moduleCodes) {
    List<String> modules = moduleCodes == null ? List.of() : moduleCodes;
    return new CacheKey(operation, modules, Collections.singletonList(moduleCodes));
  }

  boolean concerns(ParameterModule module) {
    return modules.contains(module.code());
  }
}
