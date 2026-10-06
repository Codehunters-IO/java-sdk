package io.codehunters.fileshare.config;

import org.springframework.http.client.ClientHttpRequestInterceptor;

/**
 * Intercepts every call the SDK makes to the file-share service. Declaring one or more beans of
 * this type replaces the built-in client-credentials interceptor; they run in {@code @Order}.
 */
@FunctionalInterface
public interface FileShareRequestInterceptor extends ClientHttpRequestInterceptor {}
