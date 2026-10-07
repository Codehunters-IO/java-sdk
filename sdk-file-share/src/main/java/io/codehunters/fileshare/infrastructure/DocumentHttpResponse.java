package io.codehunters.fileshare.infrastructure;

import java.util.Map;

// Identifiers and timestamps stay strings so a malformed value fails in the mapping, inside the
// adapter's error handling, with a message naming the field.
record DocumentHttpResponse(
    String id,
    String referenceId,
    String workflowId,
    Map<String, String> metadata,
    String contentType,
    long size,
    String filename,
    String status,
    String presignedUrl,
    String presignedUrlExpiresAt) {}
