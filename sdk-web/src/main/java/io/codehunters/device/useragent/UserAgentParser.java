package io.codehunters.device.useragent;

/** Turns a {@code User-Agent} header into a {@link UserAgentInfo}. */
@FunctionalInterface
public interface UserAgentParser {

  /**
   * Parses a header.
   *
   * @param userAgent the header, already sanitized and truncated; may be empty
   * @return what the header says, {@link UserAgentInfo#UNKNOWN} when nothing is recognised
   */
  UserAgentInfo parse(String userAgent);
}
