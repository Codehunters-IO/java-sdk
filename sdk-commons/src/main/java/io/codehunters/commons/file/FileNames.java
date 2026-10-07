package io.codehunters.commons.file;

import java.util.Optional;

/**
 * File-name helpers that work on plain strings, so they apply to uploaded names as well as paths.
 * Both {@code /} and {@code \} count as directory separators.
 */
public final class FileNames {

  private FileNames() {}

  /**
   * Returns the extension of the last path segment, without the dot. A leading dot (hidden files
   * such as {@code .bashrc}) and a trailing dot do not start an extension.
   *
   * @param filename a file name or path
   * @return the extension, or empty when there is none
   */
  public static Optional<String> extension(String filename) {
    requireNotNull(filename, "filename");
    String name = lastSegment(filename);
    int dot = name.lastIndexOf('.');
    if (dot <= 0 || dot == name.length() - 1) {
      return Optional.empty();
    }
    return Optional.of(name.substring(dot + 1));
  }

  /**
   * Builds a file name from {@code baseName} and the extension of {@code filename}. The directory
   * part of {@code filename} is not kept.
   *
   * @param filename the original file name or path
   * @param baseName the new name without extension
   * @return {@code baseName.extension}, or {@code baseName} when the original has no extension
   * @throws IllegalArgumentException if {@code baseName} is blank, {@code ..}, or has a separator
   */
  public static String withBaseName(String filename, String baseName) {
    requireValidBaseName(baseName);
    return extension(filename).map(ext -> baseName + "." + ext).orElse(baseName);
  }

  private static String lastSegment(String path) {
    int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return path.substring(separator + 1);
  }

  private static void requireValidBaseName(String baseName) {
    requireNotNull(baseName, "baseName");
    boolean invalid =
        baseName.isBlank()
            || baseName.equals("..")
            || baseName.indexOf('/') >= 0
            || baseName.indexOf('\\') >= 0;
    if (invalid) {
      throw new IllegalArgumentException(
          "baseName must be a non-blank name without path separators");
    }
  }

  private static void requireNotNull(String value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
  }
}
