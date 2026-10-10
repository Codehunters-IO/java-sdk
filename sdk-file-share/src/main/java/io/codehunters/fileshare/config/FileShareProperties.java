package io.codehunters.fileshare.config;

import io.codehunters.fileshare.http.TransferLimits;
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
 *     memory, at most {@link TransferLimits#MAX_DOWNLOAD_SIZE_LIMIT} bytes
 * @param uploadIdleTimeout how long an upload to a presigned URL waits for the storage's answer
 *     once the content is sent; there is no deadline for the whole upload
 * @param maxUploadSize the largest content {@code upload(ticket, content, contentLength)} sends
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
    @DefaultValue("30s") Duration uploadIdleTimeout,
    @DefaultValue("5GB") DataSize maxUploadSize,
    @DefaultValue Presigned presigned,
    @DefaultValue Auth auth) {

  /**
   * Rejects transfer sizes that cannot be applied.
   *
   * @throws IllegalArgumentException if {@code maxDownloadSize} is not positive or too large, or
   *     {@code maxUploadSize} is not positive
   */
  public FileShareProperties {
    limits(maxDownloadSize, maxUploadSize);
  }

  /**
   * The transfer size limits.
   *
   * @return the download and upload limits in bytes
   */
  public TransferLimits transferLimits() {
    return limits(maxDownloadSize, maxUploadSize);
  }

  private static TransferLimits limits(DataSize maxDownloadSize, DataSize maxUploadSize) {
    return new TransferLimits(maxDownloadSize.toBytes(), maxUploadSize.toBytes());
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
