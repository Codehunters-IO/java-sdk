package io.codehunters.fileshare.domain.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable view of a file held by the file-share service.
 *
 * @param id the file identifier
 * @param referenceId the business reference the file belongs to
 * @param workflowId the workflow the file is attached to
 * @param filename the original file name
 * @param contentType the MIME type of the content
 * @param size the content size in bytes
 * @param status the lifecycle status; {@link FileStatus#UNKNOWN} when the service sent none
 * @param metadata arbitrary key/value metadata stored alongside the file; never {@code null}
 * @throws IllegalArgumentException if a value breaks a {@link FileConstraints} rule
 */
public record FileRef(
    UUID id,
    String referenceId,
    UUID workflowId,
    String filename,
    String contentType,
    long size,
    FileStatus status,
    Map<String, String> metadata) {

  /** Validates every field and takes an unmodifiable copy of the metadata. */
  public FileRef {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(workflowId, "workflowId");
    FileConstraints.requireReferenceId(referenceId);
    FileConstraints.requireFilename(filename);
    FileConstraints.requireContentType(contentType);
    FileConstraints.requireSize(size);
    status = status == null ? FileStatus.UNKNOWN : status;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
