package io.codehunters.jasper.config;

import io.codehunters.jasper.ReportSettings;
import io.codehunters.jasper.Virtualization;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Configuration for the JasperReports SDK, bound from the {@code codehunters.jasper} prefix.
 * Invalid values fail at startup.
 *
 * @param enabled whether the SDK auto-configuration is active
 * @param templateBase the classpath folder templates, images and subreports are read from
 * @param cacheSize compiled templates kept, 0 to 10000
 * @param maxOutputSize the largest document {@code toBytes} returns, 1 byte to about 2 GB
 * @param virtualizer whether filled pages are swapped to a file
 */
@ConfigurationProperties(prefix = "codehunters.jasper")
public record JasperProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("reports") String templateBase,
    @DefaultValue("64") int cacheSize,
    @DefaultValue("50MB") DataSize maxOutputSize,
    @DefaultValue Virtualizer virtualizer) {

  private static final String PREFIX = "codehunters.jasper.";

  /** Validates the settings. */
  public JasperProperties {
    settings(templateBase, cacheSize, maxOutputSize, virtualizer);
  }

  /**
   * The renderer settings these properties describe.
   *
   * @return the settings
   */
  public ReportSettings settings() {
    return settings(templateBase, cacheSize, maxOutputSize, virtualizer);
  }

  private static ReportSettings settings(
      String templateBase, int cacheSize, DataSize maxOutputSize, Virtualizer virtualizer) {
    try {
      return new ReportSettings(
          templateBase, cacheSize, maxOutputSize.toBytes(), virtualizer.virtualization());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(PREFIX + e.getMessage());
    }
  }

  /**
   * Page swapping for large reports.
   *
   * @param enabled whether pages are swapped to a file
   * @param maxPages pages kept in memory before swapping, 1 to 100000
   * @param directory an existing, writable folder for swap files; the temporary directory when
   *     unset
   */
  public record Virtualizer(
      @DefaultValue("false") boolean enabled, @DefaultValue("100") int maxPages, Path directory) {

    /** Validates the folder when swapping is on. */
    public Virtualizer {
      if (enabled
          && directory != null
          && !(Files.isDirectory(directory) && Files.isWritable(directory))) {
        throw new IllegalArgumentException(
            PREFIX + "virtualizer.directory must be an existing, writable folder");
      }
    }

    Virtualization virtualization() {
      Path folder = directory == null ? Virtualization.OFF.directory() : directory;
      return new Virtualization(enabled, maxPages, folder);
    }
  }
}
