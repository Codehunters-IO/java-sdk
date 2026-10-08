package io.codehunters.device.useragent;

/**
 * Prepares a raw {@code User-Agent} header for parsing and storage: control characters become
 * spaces, surrounding whitespace is removed and the result is cut to a maximum length, so a long
 * header cannot make regex-based parsers slow and never ends in half a surrogate pair.
 */
public final class UserAgents {

  private UserAgents() {}

  /**
   * Sanitizes a header.
   *
   * @param raw the header as received; null is treated as empty
   * @param maxLength the maximum length in UTF-16 units, at least 1
   * @return the sanitized header, possibly empty
   */
  public static String sanitize(String raw, int maxLength) {
    if (maxLength < 1) {
      throw new IllegalArgumentException("max length must be at least 1");
    }
    if (raw == null) {
      return "";
    }
    var cleaned = new StringBuilder(Math.min(raw.length(), maxLength));
    raw.codePoints()
        .map(cp -> Character.isISOControl(cp) ? ' ' : cp)
        .forEach(cleaned::appendCodePoint);
    var text = cleaned.toString().strip();
    return truncate(text, maxLength).strip();
  }

  private static String truncate(String text, int maxLength) {
    if (text.length() <= maxLength) {
      return text;
    }
    var end = Character.isHighSurrogate(text.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
    return text.substring(0, end);
  }
}
