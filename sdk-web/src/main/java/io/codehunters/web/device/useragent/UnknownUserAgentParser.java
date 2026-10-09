package io.codehunters.web.device.useragent;

/** Used when no parser library is on the classpath: every client is unknown. */
public final class UnknownUserAgentParser implements UserAgentParser {

  /** Creates the parser. */
  public UnknownUserAgentParser() {}

  @Override
  public UserAgentInfo parse(String userAgent) {
    return UserAgentInfo.UNKNOWN;
  }
}
