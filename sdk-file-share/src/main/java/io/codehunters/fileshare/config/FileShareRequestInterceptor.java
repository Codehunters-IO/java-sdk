package io.codehunters.fileshare.config;

import org.springframework.http.client.ClientHttpRequestInterceptor;

/**
 * Intercepts every call the SDK makes to the file-share service; beans of this type run in
 * {@code @Order}. When {@code codehunters.file-share.auth.client-registration-id} is set, the SDK's
 * client-credentials step runs after them and sets the {@code Authorization} header, so these
 * interceptors add to authentication rather than replace it.
 */
@FunctionalInterface
public interface FileShareRequestInterceptor extends ClientHttpRequestInterceptor {}
