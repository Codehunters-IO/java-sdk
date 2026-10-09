package io.codehunters.fileshare.model;

import java.net.URI;

final class PresignedUrls {

  private PresignedUrls() {}

  // The query string of a presigned URL is the credential, and user info may be one too: print
  // only scheme, host, port and path.
  static String redact(URI url) {
    String port = url.getPort() == -1 ? "" : ":" + url.getPort();
    String path = url.getRawPath() == null ? "" : url.getRawPath();
    String query = url.getRawQuery() == null ? "" : "?<redacted>";
    return url.getScheme() + "://" + url.getHost() + port + path + query;
  }
}
