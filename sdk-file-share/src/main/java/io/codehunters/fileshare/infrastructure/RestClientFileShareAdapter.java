package io.codehunters.fileshare.infrastructure;

import io.codehunters.fileshare.application.FileShareClient;
import io.codehunters.fileshare.application.UploadRequest;
import io.codehunters.fileshare.domain.exception.FileShareConflictException;
import io.codehunters.fileshare.domain.exception.FileShareException;
import io.codehunters.fileshare.domain.exception.FileShareNotFoundException;
import io.codehunters.fileshare.domain.exception.FileShareUnauthorizedException;
import io.codehunters.fileshare.domain.exception.FileShareUnavailableException;
import io.codehunters.fileshare.domain.exception.FileShareValidationException;
import io.codehunters.fileshare.domain.model.FileRef;
import io.codehunters.fileshare.domain.model.FileStatus;
import io.codehunters.fileshare.domain.model.PresignedDownloadTicket;
import io.codehunters.fileshare.domain.model.PresignedUploadTicket;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * {@link FileShareClient} implementation backed by a Spring {@link RestClient}. Maps file-share
 * HTTP status codes and transport failures onto the {@link
 * io.codehunters.fileshare.domain.exception.FileShareException} hierarchy.
 */
@Slf4j
public class RestClientFileShareAdapter implements FileShareClient {

  private static final String UPLOAD_PATH =
      "/api/v1/shared/{referenceId}/documents/{workflowId}/presigned-url";
  private static final String DOCUMENT_PATH = "/api/v1/shared/{referenceId}/documents/{documentId}";
  private static final String DOCUMENT_CONTENT_PATH =
      "/api/v1/shared/{referenceId}/documents/{documentId}/content";

  private final RestClient restClient;

  /**
   * Creates an adapter that calls the file-share service through the given client.
   *
   * @param restClient the configured REST client targeting the file-share base URL
   */
  public RestClientFileShareAdapter(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public PresignedUploadTicket requestUpload(UploadRequest request) {
    var body =
        new PresignedUrlHttpRequest(
            request.filename(), request.contentType(), request.size(), request.metadata());
    var response =
        execute(
            () ->
                restClient
                    .post()
                    .uri(UPLOAD_PATH, request.referenceId(), request.workflowId())
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::mapError)
                    .body(DocumentHttpResponse.class),
            "requestUpload");
    var ref = toFileRef(response);
    return new PresignedUploadTicket(
        ref,
        URI.create(response.presignedUrl()),
        response.presignedUrlExpiresAt().toInstant(java.time.ZoneOffset.UTC));
  }

  @Override
  public PresignedDownloadTicket requestDownload(String referenceId, UUID fileId) {
    var response = fetchDocument(referenceId, fileId);
    if (response.presignedUrl() == null) {
      throw new FileShareConflictException(
          "Document is not in a downloadable state: status=" + response.status());
    }
    var ref = toFileRef(response);
    return new PresignedDownloadTicket(
        ref,
        URI.create(response.presignedUrl()),
        response.presignedUrlExpiresAt().toInstant(java.time.ZoneOffset.UTC));
  }

  @Override
  public FileRef getMetadata(String referenceId, UUID fileId) {
    return toFileRef(fetchDocument(referenceId, fileId));
  }

  /**
   * Downloads the binary by calling the file-share server-side endpoint {@code GET
   * /api/v1/shared/{referenceId}/documents/{fileId}/content}. The server reads the bytes from S3
   * via the AWS SDK ({@code S3Client.getObject(...)}) and streams them back — no presigned URL or
   * client-side SigV4 handshake is involved. Sidesteps S3 SignatureDoesNotMatch failures caused by
   * HTTP-client header drift on presigned URLs.
   */
  @Override
  public byte[] download(String referenceId, UUID fileId) {
    return execute(
        () ->
            restClient
                .get()
                .uri(DOCUMENT_CONTENT_PATH, referenceId, fileId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .body(byte[].class),
        "download");
  }

  private DocumentHttpResponse fetchDocument(String referenceId, UUID fileId) {
    return execute(
        () ->
            restClient
                .get()
                .uri(DOCUMENT_PATH, referenceId, fileId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .body(DocumentHttpResponse.class),
        "getDocument");
  }

  private FileRef toFileRef(DocumentHttpResponse response) {
    if (response == null) {
      throw new FileShareException("Empty response from file-share");
    }
    return new FileRef(
        response.id() != null ? UUID.fromString(response.id()) : null,
        response.referenceId(),
        response.workflowId(),
        response.filename(),
        response.contentType(),
        response.size(),
        parseStatus(response.status()),
        response.metadata() != null ? response.metadata() : Map.of());
  }

  private FileStatus parseStatus(String value) {
    if (value == null) {
      return null;
    }
    try {
      return FileStatus.valueOf(value);
    } catch (IllegalArgumentException e) {
      log.warn("Unknown file status received: {}", value);
      return null;
    }
  }

  private void mapError(
      org.springframework.http.HttpRequest request,
      org.springframework.http.client.ClientHttpResponse clientResponse)
      throws java.io.IOException {
    var status = clientResponse.getStatusCode();
    var body = new String(clientResponse.getBody().readAllBytes());
    String message = "file-share error status=" + status + " body=" + body;
    if (status.value() == 404) {
      throw new FileShareNotFoundException(message);
    }
    if (status.value() == 401 || status.value() == 403) {
      throw new FileShareUnauthorizedException(message);
    }
    if (status.value() == 409) {
      throw new FileShareConflictException(message);
    }
    if (status.is4xxClientError()) {
      throw new FileShareValidationException(message);
    }
    if (status.is5xxServerError()) {
      throw new FileShareUnavailableException(message);
    }
    throw new FileShareException(message);
  }

  private <T> T execute(java.util.function.Supplier<T> call, String operation) {
    try {
      return call.get();
    } catch (FileShareException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new FileShareUnavailableException(
          "I/O error calling file-share operation=" + operation, e);
    } catch (RuntimeException e) {
      throw new FileShareException("Unexpected error calling file-share operation=" + operation, e);
    }
  }
}
