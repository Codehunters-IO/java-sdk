package io.codehunters.fileshare.infrastructure;

import io.codehunters.fileshare.application.FileShareClient;
import io.codehunters.fileshare.application.UploadRequest;
import io.codehunters.fileshare.domain.exception.FileShareConflictException;
import io.codehunters.fileshare.domain.exception.FileShareException;
import io.codehunters.fileshare.domain.exception.FileShareUnavailableException;
import io.codehunters.fileshare.domain.model.FileConstraints;
import io.codehunters.fileshare.domain.model.FileRef;
import io.codehunters.fileshare.domain.model.FileStatus;
import io.codehunters.fileshare.domain.model.PresignedDownloadTicket;
import io.codehunters.fileshare.domain.model.PresignedUploadTicket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * {@link FileShareClient} implementation backed by a Spring {@link RestClient}. Maps file-share
 * HTTP status codes, transport failures and unreadable responses onto the {@link
 * FileShareException} hierarchy.
 */
public class RestClientFileShareAdapter implements FileShareClient {

  private static final String UPLOAD_PATH =
      "/api/v1/shared/{referenceId}/documents/{workflowId}/presigned-url";
  private static final String DOCUMENT_PATH = "/api/v1/shared/{referenceId}/documents/{documentId}";
  private static final String DOCUMENT_CONTENT_PATH =
      "/api/v1/shared/{referenceId}/documents/{documentId}/content";

  private final RestClient restClient;
  private final PresignedUrlPolicy presignedUrlPolicy;
  private final long maxDownloadSize;

  /**
   * Creates an adapter that calls the file-share service through the given client.
   *
   * @param restClient the configured REST client targeting the file-share base URL
   * @param presignedUrlPolicy decides which presigned URLs may reach the caller
   * @param maxDownloadSize the largest content, in bytes, {@link #download(String, UUID)} buffers
   */
  public RestClientFileShareAdapter(
      RestClient restClient, PresignedUrlPolicy presignedUrlPolicy, long maxDownloadSize) {
    this.restClient = restClient;
    this.presignedUrlPolicy = presignedUrlPolicy;
    this.maxDownloadSize = maxDownloadSize;
  }

  @Override
  public PresignedUploadTicket requestUpload(UploadRequest request) {
    var body =
        new PresignedUrlHttpRequest(
            request.filename(), request.contentType(), request.size(), request.metadata());
    return execute(
        "requestUpload",
        () -> {
          var response = post(request, body);
          return new PresignedUploadTicket(
              DocumentMapper.toFileRef(response),
              presignedUrlPolicy.requireAllowed(response.presignedUrl()),
              DocumentMapper.toExpiry(response.presignedUrlExpiresAt()));
        });
  }

  @Override
  public PresignedDownloadTicket requestDownload(String referenceId, UUID fileId) {
    FileConstraints.requireReferenceId(referenceId);
    return execute(
        "requestDownload",
        () -> {
          var response = fetchDocument(referenceId, fileId);
          var file = DocumentMapper.toFileRef(response);
          requireDownloadable(response, file);
          return new PresignedDownloadTicket(
              file,
              presignedUrlPolicy.requireAllowed(response.presignedUrl()),
              DocumentMapper.toExpiry(response.presignedUrlExpiresAt()));
        });
  }

  @Override
  public FileRef getMetadata(String referenceId, UUID fileId) {
    FileConstraints.requireReferenceId(referenceId);
    return execute(
        "getMetadata", () -> DocumentMapper.toFileRef(fetchDocument(referenceId, fileId)));
  }

  /**
   * Downloads the binary through the server-side endpoint {@code GET
   * /api/v1/shared/{referenceId}/documents/{fileId}/content}, which reads the object itself, so no
   * presigned URL is involved.
   */
  @Override
  public byte[] download(String referenceId, UUID fileId) {
    FileConstraints.requireReferenceId(referenceId);
    return execute("download", () -> exchangeContent(referenceId, fileId, this::readBounded));
  }

  @Override
  public long download(String referenceId, UUID fileId, OutputStream target) {
    FileConstraints.requireReferenceId(referenceId);
    return execute(
        "download", () -> exchangeContent(referenceId, fileId, body -> body.transferTo(target)));
  }

  private DocumentHttpResponse post(UploadRequest request, PresignedUrlHttpRequest body) {
    return restClient
        .post()
        .uri(UPLOAD_PATH, request.referenceId(), request.workflowId())
        .body(body)
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> fail(res))
        .body(DocumentHttpResponse.class);
  }

  private DocumentHttpResponse fetchDocument(String referenceId, UUID fileId) {
    return restClient
        .get()
        .uri(DOCUMENT_PATH, referenceId, fileId)
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> fail(res))
        .body(DocumentHttpResponse.class);
  }

  private <T> T exchangeContent(String referenceId, UUID fileId, BodyReader<T> reader) {
    return restClient
        .get()
        .uri(DOCUMENT_CONTENT_PATH, referenceId, fileId)
        .exchange(
            (req, res) -> {
              if (res.getStatusCode().isError()) {
                fail(res);
              }
              return reader.read(res.getBody());
            });
  }

  private byte[] readBounded(InputStream body) throws IOException {
    byte[] content = body.readNBytes((int) Math.min(maxDownloadSize + 1, Integer.MAX_VALUE - 8));
    if (content.length > maxDownloadSize) {
      throw new FileShareException(
          "Content is larger than max-download-size; use download(..., OutputStream)");
    }
    return content;
  }

  private static void requireDownloadable(DocumentHttpResponse response, FileRef file) {
    if (response.presignedUrl() == null) {
      throw new FileShareConflictException(
          "Document is not in a downloadable state: status="
              + file.status()
              + ", expected "
              + FileStatus.COMPLETED);
    }
  }

  private static void fail(ClientHttpResponse response) throws IOException {
    throw ErrorResponses.toException(response);
  }

  private static <T> T execute(String operation, Supplier<T> call) {
    try {
      return call.get();
    } catch (FileShareException e) {
      throw e;
    } catch (ResourceAccessException | UncheckedIOException e) {
      throw new FileShareUnavailableException(
          "I/O error calling file-share operation=" + operation, e);
    } catch (RuntimeException e) {
      throw new FileShareException("Unexpected error calling file-share operation=" + operation, e);
    }
  }

  @FunctionalInterface
  private interface BodyReader<T> {
    T read(InputStream body) throws IOException;
  }
}
