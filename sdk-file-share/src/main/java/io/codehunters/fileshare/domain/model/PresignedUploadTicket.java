package io.codehunters.fileshare.domain.model;

import java.net.URI;
import java.time.LocalDateTime;

/**
 * Result of an upload request: the created file plus a presigned URL the caller uses to push the
 * content directly to object storage.
 *
 * @param file the created file reference
 * @param uploadUrl the presigned URL to upload the content to
 * @param expiresAt the instant after which {@code uploadUrl} is no longer valid
 */
public record PresignedUploadTicket(FileRef file, URI uploadUrl, LocalDateTime expiresAt) {

  /**
   * HTTP method to use against {@link #uploadUrl()}.
   *
   * @return always {@code "PUT"}
   */
  public String httpMethod() {
    return "PUT";
  }
}
