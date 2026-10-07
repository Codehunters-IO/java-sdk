package io.codehunters.commons.file;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * File-name helpers that work on plain strings, so they apply to uploaded names as well as paths.
 * Both {@code /} and {@code \} count as directory separators.
 */
public final class FileNames {

  private static final Pattern PLAIN_EXTENSION = Pattern.compile("[\\p{L}\\p{N}_-]{1,16}");
  private static final String FORBIDDEN_IN_NAME = "/\\:";
  private static final String UNSAFE_BASE_NAME =
      "baseName must be a plain file name: not blank, not . or .., without separators, colons,"
          + " control or format characters, and not ending in a dot or space";

  private FileNames() {}

  /**
   * Returns the extension of the last path segment, without the dot. A leading dot (hidden files
   * such as {@code .bashrc}) and a trailing dot do not start an extension. Only extensions of 1 to
   * 16 letters, digits, {@code -} or {@code _} are returned: anything else (a NUL, a trailing
   * space, an NTFS {@code :stream}) yields empty. This is a shape check, not an allow-list; callers
   * that accept uploads must still compare the extension with the types they support.
   *
   * @param filename a file name or path
   * @return the extension, or empty when there is none or it is not a plain extension
   */
  public static Optional<String> extension(String filename) {
    requireNotNull(filename, "filename");
    String name = lastSegment(filename);
    int dot = name.lastIndexOf('.');
    if (dot <= 0) {
      return Optional.empty();
    }
    String extension = name.substring(dot + 1);
    return PLAIN_EXTENSION.matcher(extension).matches() ? Optional.of(extension) : Optional.empty();
  }

  /**
   * Builds a file name from {@code baseName} and the extension of {@code filename}. The directory
   * part of {@code filename} is not kept, and an extension that {@link #extension(String)} does not
   * return is dropped.
   *
   * @param filename the original file name or path
   * @param baseName the new name without extension
   * @return {@code baseName.extension}, or {@code baseName} when the original has no extension
   * @throws IllegalArgumentException if {@code baseName} is blank, {@code .} or {@code ..},
   *     contains a separator, a colon, a control or format character, or ends in a dot or space
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
            || baseName.equals(".")
            || baseName.equals("..")
            || baseName.endsWith(".")
            || baseName.endsWith(" ")
            || baseName.codePoints().anyMatch(FileNames::isForbidden);
    if (invalid) {
      throw new IllegalArgumentException(UNSAFE_BASE_NAME);
    }
  }

  private static boolean isForbidden(int codePoint) {
    int type = Character.getType(codePoint);
    return FORBIDDEN_IN_NAME.indexOf(codePoint) >= 0
        || type == Character.CONTROL
        || type == Character.FORMAT;
  }

  private static void requireNotNull(String value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
  }
}
