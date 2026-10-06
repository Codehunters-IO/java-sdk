package io.codehunters.fileshare.application;

import io.codehunters.fileshare.domain.model.FileConstraints;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Command describing a file to be uploaded through {@link FileShareClient#requestUpload}.
 *
 * @param referenceId the business reference the file belongs to
 * @param workflowId the workflow the file is attached to
 * @param filename the original file name
 * @param contentType the MIME type of the content
 * @param size the content size in bytes
 * @param metadata arbitrary key/value metadata stored alongside the file; never {@code null}
 * @throws IllegalArgumentException if a value breaks a {@link FileConstraints} rule
 */
public record UploadRequest(
    String referenceId,
    UUID workflowId,
    String filename,
    String contentType,
    long size,
    Map<String, String> metadata) {

  /** Validates every field and takes an unmodifiable copy of the metadata. */
  public UploadRequest {
    Objects.requireNonNull(workflowId, "workflowId");
    FileConstraints.requireReferenceId(referenceId);
    FileConstraints.requireFilename(filename);
    FileConstraints.requireContentType(contentType);
    FileConstraints.requireSize(size);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
