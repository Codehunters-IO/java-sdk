package io.codehunters.jasper;

/**
 * How the renderer loads templates and bounds its work.
 *
 * @param templateBase the classpath folder templates and their resources are read from
 * @param cacheSize compiled templates kept, 0 to 10000; 0 loads the template on every render
 * @param maxOutputBytes the largest document {@link ReportRenderer#toBytes} returns, 1 byte to
 *     about 2 GB
 * @param virtualization whether filled pages are swapped to a file
 */
public record ReportSettings(
    String templateBase, int cacheSize, long maxOutputBytes, Virtualization virtualization) {

  /** {@code reports/} on the classpath, 64 templates cached, 50 MB, no virtualization. */
  public static final ReportSettings DEFAULT =
      new ReportSettings("reports", 64, 50L * 1024 * 1024, Virtualization.OFF);

  private static final int MAX_CACHE = 10_000;
  private static final long MAX_OUTPUT = Integer.MAX_VALUE - 8L;

  /** Validates the settings. */
  public ReportSettings {
    if (!TemplateNames.isResource(templateBase)) {
      throw new IllegalArgumentException(
          "template-base must be a relative classpath folder made of letters, digits, '.', '_',"
              + " '-' and '/'");
    }
    if (cacheSize < 0 || cacheSize > MAX_CACHE) {
      throw new IllegalArgumentException("cache-size must be between 0 and " + MAX_CACHE);
    }
    if (maxOutputBytes < 1 || maxOutputBytes > MAX_OUTPUT) {
      throw new IllegalArgumentException(
          "max-output-size must be between 1 and " + MAX_OUTPUT + " bytes");
    }
    if (virtualization == null) {
      throw new IllegalArgumentException("virtualization must not be null");
    }
  }

  String resolve(String name) {
    return templateBase + "/" + name;
  }
}
