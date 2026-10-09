package io.codehunters.web.errors.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the web-errors SDK, bound from the {@code codehunters.web-errors} prefix.
 * Invalid values fail at startup.
 *
 * @param enabled whether the SDK auto-configuration is active
 * @param typeBaseUri absolute base URI, ending with {@code /}, against which each problem {@code
 *     type} is resolved from its code ({@code ORDER_NOT_FOUND} becomes {@code
 *     <base>order-not-found}); unset keeps {@code about:blank}
 * @param includeTraceId whether problem details carry the {@code traceId} extension
 */
@ConfigurationProperties(prefix = "codehunters.web-errors")
public record WebErrorsProperties(
    @DefaultValue("true") boolean enabled,
    URI typeBaseUri,
    @DefaultValue("true") boolean includeTraceId) {

  /** Validates the type base URI. */
  public WebErrorsProperties {
    if (typeBaseUri != null && !(typeBaseUri.isAbsolute() && hasDirectoryPath(typeBaseUri))) {
      throw new IllegalArgumentException(
          "codehunters.web-errors.type-base-uri must be an absolute URI ending with '/'");
    }
  }

  private static boolean hasDirectoryPath(URI uri) {
    return uri.getPath() != null && uri.getPath().endsWith("/");
  }
}
