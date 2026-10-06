package io.codehunters.fileshare.config;

import org.springframework.http.client.ClientHttpRequestInterceptor;

/**
 * The SDK's own authentication step. It runs after every {@link FileShareRequestInterceptor}, so
 * application interceptors never see the token and cannot drop it. Kept free of Spring Security
 * types so the auto-configuration loads without them.
 */
interface FileShareAuthInterceptor extends ClientHttpRequestInterceptor {}
