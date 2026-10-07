package io.codehunters.fileshare.domain.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * Result of a download request: the file plus a presigned URL the caller uses to fetch the content
 * directly from object storage. Its {@code toString()} leaves out the URL's query string, which
 * carries the signature.
 *
 * @param file the file reference
 * @param downloadUrl the presigned URL to download the content from
 * @param expiresAt the instant after which {@code downloadUrl} is no longer valid
 */
public record PresignedDownloadTicket(FileRef file, URI downloadUrl, Instant expiresAt) {

  /** Rejects missing values. */
  public PresignedDownloadTicket {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(downloadUrl, "downloadUrl");
    Objects.requireNonNull(expiresAt, "expiresAt");
  }

  /**
   * HTTP method to use against {@link #downloadUrl()}.
   *
   * @return always {@code "GET"}
   */
  public String httpMethod() {
    return "GET";
  }

  @Override
  public String toString() {
    return "PresignedDownloadTicket[file="
        + file
        + ", downloadUrl="
        + PresignedUrls.redact(downloadUrl)
        + ", expiresAt="
        + expiresAt
        + "]";
  }
}
