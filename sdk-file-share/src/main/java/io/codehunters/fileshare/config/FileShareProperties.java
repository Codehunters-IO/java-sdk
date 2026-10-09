package io.codehunters.fileshare.config;

import io.codehunters.fileshare.http.RestClientFileShareAdapter;
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
 * @param readTimeout the deadline for a whole metadata or upload call, from sending the request to
 *     reading the last byte of the response
 * @param downloadIdleTimeout how long a content download may go without receiving a byte; there is
 *     no deadline for the whole download
 * @param maxDownloadSize the largest content {@code download(referenceId, fileId)} loads into
 *     memory, at most {@link RestClientFileShareAdapter#MAX_DOWNLOAD_SIZE_LIMIT} bytes
 * @param presigned rules for the presigned URLs handed to callers
 * @param auth how calls to the service authenticate
 */
@ConfigurationProperties(prefix = "codehunters.file-share")
public record FileShareProperties(
    @DefaultValue("true") boolean enabled,
    String baseUrl,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("30s") Duration readTimeout,
    @DefaultValue("30s") Duration downloadIdleTimeout,
    @DefaultValue("50MB") DataSize maxDownloadSize,
    @DefaultValue Presigned presigned,
    @DefaultValue Auth auth) {

  /**
   * Rejects a {@code maxDownloadSize} that cannot be buffered.
   *
   * @throws IllegalArgumentException if {@code maxDownloadSize} is not positive or too large
   */
  public FileShareProperties {
    long bytes = maxDownloadSize.toBytes();
    if (bytes <= 0 || bytes > RestClientFileShareAdapter.MAX_DOWNLOAD_SIZE_LIMIT) {
      throw new IllegalArgumentException(
          "codehunters.file-share.max-download-size must be between 1B and "
              + RestClientFileShareAdapter.MAX_DOWNLOAD_SIZE_LIMIT
              + "B, was "
              + maxDownloadSize);
    }
  }

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
   *     client-credentials token is sent as a bearer token; none when unset or blank
   * @param principal the principal name the token is cached under
   * @param allowInsecureHttp whether the token may be sent to a plain {@code http} base URL
   */
  public record Auth(
      String clientRegistrationId,
      @DefaultValue("codehunters-file-share") String principal,
      @DefaultValue("false") boolean allowInsecureHttp) {

    /** Treats a blank registration id as unset. */
    public Auth {
      clientRegistrationId =
          clientRegistrationId == null || clientRegistrationId.isBlank()
              ? null
              : clientRegistrationId;
    }
  }
}
