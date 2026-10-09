package io.codehunters.fileshare.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Rules every file reference and upload request must satisfy. They keep path segments and headers
 * built from these values safe: a reference id is a single URL path segment and a filename never
 * carries a directory or a control character.
 */
public final class FileConstraints {

  private static final Pattern REFERENCE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,255}$");
  private static final String TOKEN = "[!#$%&'*+.^_`|~0-9A-Za-z-]+";
  private static final Pattern CONTENT_TYPE =
      Pattern.compile("^" + TOKEN + "/" + TOKEN + "(\\s*;[^\\p{Cntrl}]*)?$");
  private static final Pattern UNSAFE_FILENAME = Pattern.compile("[/\\\\\\p{Cntrl}]");
  private static final int MAX_FILENAME_LENGTH = 255;

  private FileConstraints() {}

  /**
   * Checks a business reference id: letters, digits, {@code .}, {@code _} or {@code -}, and never
   * {@code .} or {@code ..} alone.
   *
   * @param referenceId the value to check
   * @return the same value
   * @throws IllegalArgumentException if the value is not a safe path segment
   */
  public static String requireReferenceId(String referenceId) {
    Objects.requireNonNull(referenceId, "referenceId");
    if (!REFERENCE_ID.matcher(referenceId).matches() || isDotSegment(referenceId)) {
      throw new IllegalArgumentException("referenceId is not a safe path segment");
    }
    return referenceId;
  }

  /**
   * Checks a file name: not blank, at most 255 characters, with no path separator or control
   * character.
   *
   * @param filename the value to check
   * @return the same value
   * @throws IllegalArgumentException if the value is not a plain file name
   */
  public static String requireFilename(String filename) {
    Objects.requireNonNull(filename, "filename");
    if (filename.isBlank()
        || filename.length() > MAX_FILENAME_LENGTH
        || isDotSegment(filename)
        || UNSAFE_FILENAME.matcher(filename).find()) {
      throw new IllegalArgumentException("filename must be a plain file name");
    }
    return filename;
  }

  /**
   * Checks a MIME type such as {@code text/plain; charset=UTF-8}.
   *
   * @param contentType the value to check
   * @return the same value
   * @throws IllegalArgumentException if the value is not a {@code type/subtype} media type
   */
  public static String requireContentType(String contentType) {
    Objects.requireNonNull(contentType, "contentType");
    if (!CONTENT_TYPE.matcher(contentType).matches()) {
      throw new IllegalArgumentException("contentType is not a media type");
    }
    return contentType;
  }

  /**
   * Checks a content size.
   *
   * @param size the size in bytes
   * @return the same value
   * @throws IllegalArgumentException if the size is negative
   */
  public static long requireSize(long size) {
    if (size < 0) {
      throw new IllegalArgumentException("size must not be negative");
    }
    return size;
  }

  private static boolean isDotSegment(String value) {
    return ".".equals(value) || "..".equals(value);
  }
}
