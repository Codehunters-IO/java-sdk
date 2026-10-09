package io.codehunters.web.device.useragent;

/**
 * What a {@code User-Agent} header says about the client. Families are {@code "Other"} and versions
 * empty when they could not be recognised; no component is ever null.
 *
 * @param browserFamily the browser or client library, e.g. {@code Chrome}
 * @param browserVersion the browser version, e.g. {@code 120.0.6099}
 * @param osFamily the operating system, e.g. {@code Windows}
 * @param osVersion the operating system version, e.g. {@code 10}
 * @param deviceFamily the device, e.g. {@code iPhone}
 */
public record UserAgentInfo(
    String browserFamily,
    String browserVersion,
    String osFamily,
    String osVersion,
    String deviceFamily) {

  /** Family name used when a component could not be recognised. */
  public static final String OTHER = "Other";

  /** A client nothing is known about. */
  public static final UserAgentInfo UNKNOWN = new UserAgentInfo(OTHER, "", OTHER, "", OTHER);

  /** Rejects null components. */
  public UserAgentInfo {
    if (browserFamily == null
        || browserVersion == null
        || osFamily == null
        || osVersion == null
        || deviceFamily == null) {
      throw new IllegalArgumentException("user agent components must not be null");
    }
  }

  /**
   * Whether nothing was recognised.
   *
   * @return {@code true} when this equals {@link #UNKNOWN}
   */
  public boolean isUnknown() {
    return UNKNOWN.equals(this);
  }
}
