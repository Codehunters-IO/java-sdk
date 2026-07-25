package io.codehunters.fileshare.infrastructure;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

record DocumentHttpResponse(
    String id,
    String referenceId,
    UUID workflowId,
    Map<String, String> metadata,
    String contentType,
    long size,
    String filename,
    String status,
    String presignedUrl,
    LocalDateTime presignedUrlExpiresAt) {}
