package io.codehunters.fileshare.http;

import io.codehunters.fileshare.FileShareClient;
import java.util.Objects;
import org.springframework.web.client.RestClient;

/**
 * The REST clients behind the SDK's {@link FileShareClient}. The auto-configuration builds them;
 * applications normally inject {@link FileShareClient} instead.
 *
 * @param api calls the file-share service's metadata and upload-request endpoints; carries the
 *     service base URL and authentication
 * @param content downloads content from the service; same base URL and authentication, a timeout
 *     between bytes instead of a deadline, and no redirects
 * @param upload sends content to presigned upload URLs; no base URL, no authentication, no
 *     application interceptors and no redirects, so nothing but the signed request reaches object
 *     storage
 */
public record RestFileShareClients(RestClient api, RestClient content, RestClient upload) {

  /** Rejects missing clients. */
  public RestFileShareClients {
    Objects.requireNonNull(api, "api");
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(upload, "upload");
  }

  /**
   * Builds the REST-backed {@link FileShareClient}.
   *
   * @param presignedUrlPolicy decides which presigned URLs may reach the caller or receive content
   * @param limits the transfer size limits
   * @return the client
   */
  public FileShareClient client(PresignedUrlPolicy presignedUrlPolicy, TransferLimits limits) {
    return new RestClientFileShareAdapter(this, presignedUrlPolicy, limits);
  }
}
