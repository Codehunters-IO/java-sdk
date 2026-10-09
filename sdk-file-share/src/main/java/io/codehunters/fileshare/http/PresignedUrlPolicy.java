package io.codehunters.fileshare.http;

import io.codehunters.fileshare.FileShareException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether a presigned URL handed out by the file-share service may reach the caller: it
 * must be an absolute {@code https} URL and, when hosts are configured, point at one of them on the
 * default port. An entry such as {@code *.s3.amazonaws.com} matches any subdomain, not the domain
 * itself.
 */
public final class PresignedUrlPolicy {

  private static final int HTTPS_PORT = 443;

  private final List<String> allowedHosts;

  /**
   * Creates the policy.
   *
   * @param allowedHosts exact hosts or {@code *.}-prefixed wildcards; empty allows any host
   */
  public PresignedUrlPolicy(List<String> allowedHosts) {
    this.allowedHosts =
        allowedHosts.stream().map(host -> host.trim().toLowerCase(Locale.ROOT)).toList();
  }

  /**
   * Parses and checks a presigned URL.
   *
   * @param presignedUrl the URL from the service response
   * @return the parsed URL
   * @throws FileShareException if the URL is missing, malformed, not https or on another host
   */
  public URI requireAllowed(String presignedUrl) {
    URI url = parse(presignedUrl);
    if (!"https".equalsIgnoreCase(url.getScheme())) {
      throw new FileShareException("presignedUrl must use https");
    }
    if (url.getHost() == null || !isAllowed(url.getHost().toLowerCase(Locale.ROOT))) {
      throw new FileShareException("presignedUrl host is not allowed: " + url.getHost());
    }
    if (!allowedHosts.isEmpty() && !isDefaultHttpsPort(url.getPort())) {
      throw new FileShareException("presignedUrl port is not allowed: " + url.getPort());
    }
    return url;
  }

  private static URI parse(String presignedUrl) {
    if (presignedUrl == null) {
      throw new FileShareException("presignedUrl is missing from the response");
    }
    try {
      return new URI(presignedUrl);
    } catch (URISyntaxException e) {
      throw new FileShareException("presignedUrl is not a valid URI");
    }
  }

  /**
   * Tells whether any https host is accepted.
   *
   * @return {@code true} when no hosts are configured
   */
  public boolean allowsAnyHost() {
    return allowedHosts.isEmpty();
  }

  private static boolean isDefaultHttpsPort(int port) {
    return port == -1 || port == HTTPS_PORT;
  }

  private boolean isAllowed(String host) {
    return allowedHosts.isEmpty() || allowedHosts.stream().anyMatch(entry -> matches(entry, host));
  }

  private static boolean matches(String entry, String host) {
    return entry.startsWith("*.")
        ? host.endsWith(entry.substring(1)) && host.length() > entry.length() - 1
        : entry.equals(host);
  }
}
