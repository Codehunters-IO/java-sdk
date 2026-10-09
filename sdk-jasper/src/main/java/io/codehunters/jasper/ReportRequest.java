package io.codehunters.jasper;

import io.codehunters.jasper.internal.template.TemplateNames;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What to render: a classpath template, its parameters, its rows and the output format.
 *
 * @param template the template name under the configured base folder, ending in {@code .jasper}
 *     (precompiled, preferred) or {@code .jrxml}
 * @param parameters the report parameters, unmodifiable; values may be null but never a URL, URI,
 *     File, Path, stream or reader, which JasperReports would load from directly, nor hold one in a
 *     collection, map or array nested up to 8 levels deep; nested collections, maps and arrays are
 *     copied, and one that contains itself is refused (best effort: other containers such as {@code
 *     Optional} or beans are not looked into)
 * @param data the rows
 * @param format the output format
 */
public record ReportRequest(
    String template, Map<String, Object> parameters, ReportData data, ReportFormat format) {

  // Built-in parameters the renderer sets itself or that would bypass its classpath-only loading.
  private static final Set<String> MANAGED =
      Set.of(
          "REPORT_VIRTUALIZER",
          "REPORT_CLASS_LOADER",
          "REPORT_CONNECTION",
          "REPORT_DATA_SOURCE",
          "REPORT_CONTEXT",
          "REPORT_URL_HANDLER_FACTORY",
          "REPORT_FILE_RESOLVER",
          "REPORT_PARAMETERS_MAP",
          "JASPER_REPORTS_CONTEXT");

  // Collections, maps and arrays nested inside a parameter value; deeper values are refused.
  private static final int MAX_NESTING = 8;

  /** Validates the request and copies the parameters. */
  public ReportRequest {
    if (!TemplateNames.isTemplate(template)) {
      throw new IllegalArgumentException(TemplateNames.TEMPLATE_RULE);
    }
    parameters = copy(parameters);
    if (data == null) {
      throw new IllegalArgumentException("data must not be null");
    }
    if (format == null) {
      throw new IllegalArgumentException("format must not be null");
    }
  }

  /**
   * A request with no parameters and no rows.
   *
   * @param template the template name under the base folder
   * @param format the output format
   * @return the request
   */
  public static ReportRequest of(String template, ReportFormat format) {
    return new ReportRequest(template, Map.of(), ReportData.empty(), format);
  }

  /**
   * The same request with these parameters.
   *
   * @param parameters the parameters, copied; values may be null
   * @return the new request
   */
  public ReportRequest withParameters(Map<String, ?> parameters) {
    return new ReportRequest(template, copy(parameters), data, format);
  }

  /**
   * The same request with these rows.
   *
   * @param data the rows
   * @return the new request
   */
  public ReportRequest withData(ReportData data) {
    return new ReportRequest(template, parameters, data, format);
  }

  /** Names the parameters and counts the rows; values never appear. */
  @Override
  public String toString() {
    return "ReportRequest[template=%s, format=%s, parameters=%s, data=%s]"
        .formatted(template, format, parameters.keySet(), data);
  }

  private static Map<String, Object> copy(Map<String, ?> parameters) {
    if (parameters == null) {
      throw new IllegalArgumentException("parameters must not be null");
    }
    var copy = new LinkedHashMap<String, Object>();
    parameters.forEach((name, value) -> copy.put(checkName(name), new Scan(name).visit(value, 0)));
    return Collections.unmodifiableMap(copy);
  }

  /**
   * Walks one parameter value. JasperReports loads an image, subreport or style template directly
   * from a URL, URI, File, Path, stream or reader, bypassing the classpath-only repository; a
   * stream may even be deserialized as a report. Such values are looked for in collections, map
   * keys and values, and object arrays, at most {@value #MAX_NESTING} levels deep; a structure that
   * contains itself is refused. Every container is copied as it is checked (lists and other
   * collections to unmodifiable lists, sets to unmodifiable sets, maps to unmodifiable maps, arrays
   * to new arrays), so changing the caller's collections later cannot slip a location in. This is
   * best effort: only {@link Collection}, {@link Map} and {@code Object[]} are traversed, not
   * {@code Optional}, {@code Map.Entry}, other {@code Iterable}s or beans; a lazily loaded
   * collection is loaded, and one modified concurrently fails with {@code
   * ConcurrentModificationException}.
   */
  private static final class Scan {

    private final String name;
    private final Set<Object> path = Collections.newSetFromMap(new IdentityHashMap<>());

    Scan(String name) {
      this.name = name;
    }

    Object visit(Object value, int depth) {
      if (isLocation(value)) {
        throw rejected(
            "holds a URL, URI, File, Path, stream or reader; pass a classpath location"
                + " as a String");
      }
      if (!isContainer(value)) {
        return value;
      }
      return enter(value, depth);
    }

    private Object enter(Object value, int depth) {
      if (depth == MAX_NESTING) {
        throw rejected(
            "nests collections, maps or arrays more than " + MAX_NESTING + " levels deep");
      }
      if (!path.add(value)) {
        throw rejected("holds a collection, map or array that contains itself");
      }
      try {
        return copyOf(value, depth + 1);
      } finally {
        path.remove(value);
      }
    }

    private Object copyOf(Object value, int depth) {
      return switch (value) {
        case Map<?, ?> map -> copyOf(map, depth);
        case Set<?> set -> Collections.unmodifiableSet(collect(set, new LinkedHashSet<>(), depth));
        case Collection<?> values ->
            Collections.unmodifiableList(collect(values, new ArrayList<>(), depth));
        case Object[] array -> copyOf(array, depth);
        default -> value;
      };
    }

    private Map<Object, Object> copyOf(Map<?, ?> map, int depth) {
      var copy = new LinkedHashMap<Object, Object>();
      map.forEach((key, element) -> copy.put(visit(key, depth), visit(element, depth)));
      return Collections.unmodifiableMap(copy);
    }

    private <C extends Collection<Object>> C collect(Collection<?> values, C copy, int depth) {
      values.forEach(element -> copy.add(visit(element, depth)));
      return copy;
    }

    private Object[] copyOf(Object[] array, int depth) {
      var copy = array.clone();
      Arrays.setAll(copy, index -> visit(array[index], depth));
      return copy;
    }

    private IllegalArgumentException rejected(String reason) {
      return new IllegalArgumentException("parameter " + name + " " + reason);
    }
  }

  private static boolean isContainer(Object value) {
    return value instanceof Collection<?>
        || value instanceof Map<?, ?>
        || value instanceof Object[];
  }

  private static boolean isLocation(Object value) {
    return value instanceof URL
        || value instanceof URI
        || value instanceof File
        || value instanceof Path
        || value instanceof InputStream
        || value instanceof Reader;
  }

  private static String checkName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("parameter names must not be null or blank");
    }
    if (MANAGED.contains(name)) {
      throw new IllegalArgumentException("parameter " + name + " is managed by the SDK");
    }
    return name;
  }
}
