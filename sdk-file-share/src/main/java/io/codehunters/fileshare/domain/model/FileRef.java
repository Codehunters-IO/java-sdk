package io.codehunters.fileshare.domain.model;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable view of a file held by the file-share service.
 *
 * @param id the file identifier, or {@code null} when not yet assigned
 * @param referenceId the business reference the file belongs to
 * @param workflowId the workflow the file is attached to
 * @param filename the original file name
 * @param contentType the MIME type of the content
 * @param size the content size in bytes
 * @param status the lifecycle status, or {@code null} when unknown
 * @param metadata arbitrary key/value metadata stored alongside the file
 */
public record FileRef(
    UUID id,
    String referenceId,
    UUID workflowId,
    String filename,
    String contentType,
    long size,
    FileStatus status,
    Map<String, String> metadata) {}
