package io.codehunters.fileshare.model;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Result of an upload request: the created file plus a presigned URL the caller uses to push the
 * content directly to object storage. Its {@code toString()} leaves out the URL's query string,
 * which carries the signature.
 *
 * @param file the created file reference
 * @param uploadUrl the presigned URL to upload the content to
 * @param expiresAt the instant after which {@code uploadUrl} is no longer valid
 */
public record PresignedUploadTicket(FileRef file, URI uploadUrl, Instant expiresAt) {

  /** Rejects missing values. */
  public PresignedUploadTicket {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(uploadUrl, "uploadUrl");
    Objects.requireNonNull(expiresAt, "expiresAt");
  }

  /**
   * HTTP method to use against {@link #uploadUrl()}.
   *
   * @return always {@code "PUT"}
   */
  public String httpMethod() {
    return "PUT";
  }

  /**
   * Headers the upload request must carry because {@link #uploadUrl()} was signed for them: the
   * {@code Content-Type} declared when the upload was requested. Sending another value breaks the
   * signature.
   *
   * @return header name to value, unmodifiable
   */
  public Map<String, String> requiredHeaders() {
    return Map.of("Content-Type", file.contentType());
  }

  @Override
  public String toString() {
    return "PresignedUploadTicket[file="
        + file
        + ", uploadUrl="
        + PresignedUrls.redact(uploadUrl)
        + ", expiresAt="
        + expiresAt
        + "]";
  }
}
