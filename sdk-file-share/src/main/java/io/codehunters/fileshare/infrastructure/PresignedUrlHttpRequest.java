package io.codehunters.fileshare.infrastructure;

import java.util.Map;

record PresignedUrlHttpRequest(
    String filename, String contentType, long size, Map<String, String> metadata) {}
