package io.codehunters.fileshare.domain.model;

import java.net.URI;
import java.time.LocalDateTime;

/**
 * Result of a download request: the file plus a presigned URL the caller uses to fetch the content
 * directly from object storage.
 *
 * @param file the file reference
 * @param downloadUrl the presigned URL to download the content from
 * @param expiresAt the instant after which {@code downloadUrl} is no longer valid
 */
public record PresignedDownloadTicket(FileRef file, URI downloadUrl, LocalDateTime expiresAt) {

  /**
   * HTTP method to use against {@link #downloadUrl()}.
   *
   * @return always {@code "GET"}
   */
  public String httpMethod() {
    return "GET";
  }
}
