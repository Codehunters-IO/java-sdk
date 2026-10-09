package io.codehunters.web.errors.servlet;

import java.net.URI;

/**
 * How problem details are built.
 *
 * @param typeBaseUri the base the {@code type} URI is resolved against, or {@code null} to keep
 *     {@code about:blank}
 * @param includeTraceId whether to add the {@code traceId} extension
 */
public record ProblemSettings(URI typeBaseUri, boolean includeTraceId) {}
