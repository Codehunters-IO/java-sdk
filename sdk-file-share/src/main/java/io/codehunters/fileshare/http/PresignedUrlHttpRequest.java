package io.codehunters.fileshare.http;

import java.util.Map;

record PresignedUrlHttpRequest(
    String filename, String contentType, long size, Map<String, String> metadata) {}
