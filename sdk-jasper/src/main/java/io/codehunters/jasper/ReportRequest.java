package io.codehunters.jasper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What to render: a classpath template, its parameters, its rows and the output format.
 *
 * @param template the template name under the configured base folder, ending in {@code .jasper}
 *     (precompiled, preferred) or {@code .jrxml}
 * @param parameters the report parameters, unmodifiable; values may be null
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
    return Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
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
