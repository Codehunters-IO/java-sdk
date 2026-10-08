package io.codehunters.device.uap;

import io.codehunters.device.useragent.UserAgentInfo;
import io.codehunters.device.useragent.UserAgentParser;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ua_parser.Client;
import ua_parser.Parser;

/**
 * {@link UserAgentParser} backed by {@code ua-parser} ({@code com.github.ua-parser:uap-java}).
 * Building it loads and compiles the bundled regex list, so create it once; parsing is thread-safe.
 * Wrap it in a {@link io.codehunters.device.useragent.CachingUserAgentParser} and feed it headers
 * cut by {@link io.codehunters.device.useragent.UserAgents#sanitize}.
 */
public final class UapUserAgentParser implements UserAgentParser {

  private final Parser parser;

  /** Creates the parser with the regexes bundled in {@code uap-java}. */
  public UapUserAgentParser() {
    this.parser = new Parser();
  }

  @Override
  public UserAgentInfo parse(String userAgent) {
    Client client = parser.parse(userAgent);
    return new UserAgentInfo(
        family(client.userAgent.family),
        version(client.userAgent.major, client.userAgent.minor, client.userAgent.patch),
        family(client.os.family),
        version(client.os.major, client.os.minor, client.os.patch),
        family(client.device.family));
  }

  private static String family(String value) {
    return value == null || value.isBlank() ? UserAgentInfo.OTHER : value;
  }

  private static String version(String... parts) {
    return Stream.of(parts)
        .takeWhile(Objects::nonNull)
        .filter(part -> !part.isBlank())
        .collect(Collectors.joining("."));
  }
}
