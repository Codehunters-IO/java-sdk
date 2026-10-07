package io.codehunters.jasper;

import java.nio.file.Path;

/**
 * Whether filled pages beyond a count are swapped to a file instead of held in memory. Large
 * reports need it; the swap file is deleted when the render ends, successful or not.
 *
 * @param enabled whether pages are swapped
 * @param maxPages pages kept in memory before swapping, 1 to 100000
 * @param directory where swap files are written
 */
public record Virtualization(boolean enabled, int maxPages, Path directory) {

  /** No swapping; the folder is the temporary directory, used only if enabled later. */
  public static final Virtualization OFF =
      new Virtualization(false, 100, Path.of(System.getProperty("java.io.tmpdir")));

  private static final int MAX_PAGES = 100_000;

  /** Validates the settings. */
  public Virtualization {
    if (maxPages < 1 || maxPages > MAX_PAGES) {
      throw new IllegalArgumentException(
          "virtualizer.max-pages must be between 1 and " + MAX_PAGES);
    }
    if (directory == null) {
      throw new IllegalArgumentException("virtualizer.directory must not be null");
    }
  }

  /**
   * Swaps pages beyond {@code maxPages} to a file in {@code directory}.
   *
   * @param maxPages pages kept in memory
   * @param directory where swap files are written
   * @return the settings
   */
  public static Virtualization swapFile(int maxPages, Path directory) {
    return new Virtualization(true, maxPages, directory);
  }
}
