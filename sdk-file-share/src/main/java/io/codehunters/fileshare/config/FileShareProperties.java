package io.codehunters.fileshare.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Configuration for the file-share SDK, bound from the {@code codehunters.file-share} prefix.
 *
 * @param enabled whether the SDK auto-configuration is active
 * @param baseUrl the file-share service base URL, absolute {@code http} or {@code https}; required
 *     when {@code enabled}
 * @param connectTimeout how long to wait for the TCP connection
 * @param readTimeout how long to wait for the response
 * @param maxDownloadSize the largest content {@code download(referenceId, fileId)} loads into
 *     memory
 * @param presigned rules for the presigned URLs handed to callers
 * @param auth how calls to the service authenticate
 */
@ConfigurationProperties(prefix = "codehunters.file-share")
public record FileShareProperties(
    @DefaultValue("true") boolean enabled,
    String baseUrl,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("30s") Duration readTimeout,
    @DefaultValue("50MB") DataSize maxDownloadSize,
    @DefaultValue Presigned presigned,
    @DefaultValue Auth auth) {

  /**
   * Rules for presigned URLs.
   *
   * @param allowedHosts hosts a presigned URL may point at, exact or as {@code *.domain}; empty
   *     allows any host. URLs must always use https.
   */
  public record Presigned(@DefaultValue List<String> allowedHosts) {}

  /**
   * Outbound authentication.
   *
   * @param clientRegistrationId the Spring Security OAuth2 client registration whose
   *     client-credentials token is sent as a bearer token; none when unset
   * @param principal the principal name the token is cached under
   */
  public record Auth(
      String clientRegistrationId, @DefaultValue("codehunters-file-share") String principal) {}
}
