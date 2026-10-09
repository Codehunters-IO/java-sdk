package io.codehunters.jasper;

import io.codehunters.jasper.internal.template.TemplateNames;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What to render: a classpath template, its parameters, its rows and the output format.
 *
 * @param template the template name under the configured base folder, ending in {@code .jasper}
 *     (precompiled, preferred) or {@code .jrxml}
 * @param parameters the report parameters, unmodifiable; values may be null but never a URL, URI,
 *     File, Path, stream or reader, which JasperReports would load from directly, nor hold one in a
 *     collection, map or array nested up to 8 levels deep
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
    parameters.keySet().forEach(ReportRequest::checkName);
    parameters.forEach(ReportRequest::checkValue);
    return Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
  }

  // JasperReports loads an image, subreport or style template directly from a value of these types,
  // bypassing the classpath-only repository; a stream may even be deserialized as a report. They
  // are
  // looked for at any depth, in map keys too; cycles are visited once and the nesting is bounded.
  private static void checkValue(String name, Object value) {
    switch (scan(value, 0, Collections.newSetFromMap(new IdentityHashMap<>()))) {
      case LOCATION ->
          throw new IllegalArgumentException(
              "parameter "
                  + name
                  + " holds a URL, URI, File, Path, stream or reader; pass a classpath location as"
                  + " a String");
      case TOO_DEEP ->
          throw new IllegalArgumentException(
              "parameter "
                  + name
                  + " nests collections, maps or arrays more than "
                  + MAX_NESTING
                  + " levels deep");
      case CLEAN -> {}
    }
  }

  private enum Finding {
    CLEAN,
    LOCATION,
    TOO_DEEP
  }

  private static Finding scan(Object value, int depth, Set<Object> visited) {
    if (isLocation(value)) {
      return Finding.LOCATION;
    }
    if (!isContainer(value) || !visited.add(value)) {
      return Finding.CLEAN;
    }
    if (depth == MAX_NESTING) {
      return Finding.TOO_DEEP;
    }
    return children(value)
        .map(child -> scan(child, depth + 1, visited))
        .filter(finding -> finding != Finding.CLEAN)
        .findFirst()
        .orElse(Finding.CLEAN);
  }

  private static boolean isContainer(Object value) {
    return value instanceof Collection<?>
        || value instanceof Map<?, ?>
        || value instanceof Object[];
  }

  private static Stream<?> children(Object value) {
    return switch (value) {
      case Collection<?> values -> values.stream();
      case Map<?, ?> values -> Stream.concat(values.keySet().stream(), values.values().stream());
      case Object[] values -> Arrays.stream(values);
      default -> Stream.empty();
    };
  }

  private static boolean isLocation(Object value) {
    return value instanceof URL
        || value instanceof URI
        || value instanceof File
        || value instanceof Path
        || value instanceof InputStream
        || value instanceof Reader;
  }

  private static void checkName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("parameter names must not be null or blank");
    }
    if (MANAGED.contains(name)) {
      throw new IllegalArgumentException("parameter " + name + " is managed by the SDK");
    }
  }
}
