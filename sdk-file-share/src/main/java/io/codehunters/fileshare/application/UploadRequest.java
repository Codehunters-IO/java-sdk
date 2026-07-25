package io.codehunters.fileshare.application;

import java.util.Map;
import java.util.UUID;

/**
 * Command describing a file to be uploaded through {@link FileShareClient#requestUpload}.
 *
 * @param referenceId the business reference the file belongs to
 * @param workflowId the workflow the file is attached to
 * @param filename the original file name
 * @param contentType the MIME type of the content
 * @param size the content size in bytes
 * @param metadata arbitrary key/value metadata stored alongside the file
 */
public record UploadRequest(
    String referenceId,
    UUID workflowId,
    String filename,
    String contentType,
    long size,
    Map<String, String> metadata) {}
