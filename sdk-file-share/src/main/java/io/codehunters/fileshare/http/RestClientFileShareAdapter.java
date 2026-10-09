package io.codehunters.fileshare.http;

import io.codehunters.fileshare.FileShareClient;
import io.codehunters.fileshare.FileShareConflictException;
import io.codehunters.fileshare.FileShareException;
import io.codehunters.fileshare.FileShareUnavailableException;
import io.codehunters.fileshare.UploadRequest;
import io.codehunters.fileshare.model.FileConstraints;
import io.codehunters.fileshare.model.FileRef;
import io.codehunters.fileshare.model.FileStatus;
import io.codehunters.fileshare.model.PresignedDownloadTicket;
import io.codehunters.fileshare.model.PresignedUploadTicket;
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

  /**
   * The largest {@code max-download-size}: one byte less than the biggest array the JVM reliably
   * allocates, since one extra byte is read to detect oversized content.
   */
  public static final long MAX_DOWNLOAD_SIZE_LIMIT = Integer.MAX_VALUE - 9;

  private final RestClient restClient;
  private final RestClient contentRestClient;
  private final PresignedUrlPolicy presignedUrlPolicy;
  private final long maxDownloadSize;

  /**
   * Creates an adapter that calls the file-share service through the given client.
   *
   * @param restClient the configured REST client targeting the file-share base URL
   * @param presignedUrlPolicy decides which presigned URLs may reach the caller
   * @param maxDownloadSize the largest content, in bytes, {@link #download(String, UUID)} buffers
   * @throws IllegalArgumentException if {@code maxDownloadSize} is not positive or exceeds {@link
   *     #MAX_DOWNLOAD_SIZE_LIMIT}
   */
  public RestClientFileShareAdapter(
      RestClient restClient, PresignedUrlPolicy presignedUrlPolicy, long maxDownloadSize) {
    if (maxDownloadSize <= 0 || maxDownloadSize > MAX_DOWNLOAD_SIZE_LIMIT) {
      throw new IllegalArgumentException(
          "max-download-size must be between 1 byte and " + MAX_DOWNLOAD_SIZE_LIMIT + " bytes");
    }
    this.restClient = restClient;
    this.contentRestClient = restClient;
    this.presignedUrlPolicy = presignedUrlPolicy;
    this.maxDownloadSize = maxDownloadSize;
  }

  private RestClientFileShareAdapter(
      RestClientFileShareAdapter base, RestClient contentRestClient) {
    this.restClient = base.restClient;
    this.contentRestClient = contentRestClient;
    this.presignedUrlPolicy = base.presignedUrlPolicy;
    this.maxDownloadSize = base.maxDownloadSize;
  }

  /**
   * Returns a copy that downloads content through another client, typically one whose timeouts suit
   * long transfers.
   *
   * @param contentRestClient the client for the content endpoint, targeting the same base URL
   * @return a new adapter; this one is unchanged
   */
  public RestClientFileShareAdapter withContentClient(RestClient contentRestClient) {
    return new RestClientFileShareAdapter(this, contentRestClient);
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

  /**
   * {@inheritDoc}
   *
   * @throws UncheckedIOException if writing to {@code target} fails; the service is not at fault
   */
  @Override
  public long download(String referenceId, UUID fileId, OutputStream target) {
    FileConstraints.requireReferenceId(referenceId);
    var guarded = new TargetOutputStream(target);
    return execute(
        "download", () -> exchangeContent(referenceId, fileId, body -> body.transferTo(guarded)));
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
    return contentRestClient
        .get()
        .uri(DOCUMENT_CONTENT_PATH, referenceId, fileId)
        .exchange(
            (req, res) -> {
              if (!res.getStatusCode().is2xxSuccessful()) {
                fail(res);
              }
              return reader.read(res.getBody());
            });
  }

  private byte[] readBounded(InputStream body) throws IOException {
    byte[] content = body.readNBytes((int) (maxDownloadSize + 1));
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
    } catch (TargetOutputStream.WriteException e) {
      throw new UncheckedIOException(
          "Could not write file-share content to the target", e.getCause());
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
