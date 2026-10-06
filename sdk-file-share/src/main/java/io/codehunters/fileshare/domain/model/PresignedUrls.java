package io.codehunters.fileshare.domain.model;

import java.net.URI;

final class PresignedUrls {

  private PresignedUrls() {}

  // The query string of a presigned URL is the credential: never print it.
  static String redact(URI url) {
    return url.getRawQuery() == null
        ? url.toString()
        : url.toString().substring(0, url.toString().indexOf('?')) + "?<redacted>";
  }
}
