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
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * {@link FileShareClient} implementation backed by Spring {@link RestClient}s. Maps file-share HTTP
 * status codes, transport failures and unreadable responses onto the {@link FileShareException}
 * hierarchy. Built through {@link RestFileShareClients#client}.
 */
final class RestClientFileShareAdapter implements FileShareClient {

  private static final String UPLOAD_PATH =
      "/api/v1/shared/{referenceId}/documents/{workflowId}/presigned-url";
  private static final String DOCUMENT_PATH = "/api/v1/shared/{referenceId}/documents/{documentId}";
  private static final String DOCUMENT_CONTENT_PATH =
      "/api/v1/shared/{referenceId}/documents/{documentId}/content";

  private final RestFileShareClients clients;
  private final PresignedUrlPolicy presignedUrlPolicy;
  private final TransferLimits limits;

  RestClientFileShareAdapter(
      RestFileShareClients clients, PresignedUrlPolicy presignedUrlPolicy, TransferLimits limits) {
    this.clients = Objects.requireNonNull(clients, "clients");
    this.presignedUrlPolicy = Objects.requireNonNull(presignedUrlPolicy, "presignedUrlPolicy");
    this.limits = Objects.requireNonNull(limits, "limits");
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

  /**
   * Sends the content with a {@code PUT} to the ticket's URL, with the headers the URL was signed
   * for and a fixed {@code Content-Length}. The request carries no service token and follows no
   * redirect.
   *
   * @throws IllegalArgumentException if {@code contentLength} is negative or above {@code
   *     max-upload-size}, or {@code content} ends before {@code contentLength} bytes
   * @throws UncheckedIOException if reading {@code content} fails; the storage is not at fault
   */
  @Override
  public void upload(PresignedUploadTicket ticket, InputStream content, long contentLength) {
    requireUploadable(contentLength);
    URI url = presignedUrlPolicy.requireAllowed(ticket.uploadUrl().toString());
    var body = new UploadContent(content, contentLength);
    execute("upload", () -> put(url, ticket, body));
  }

  private void requireUploadable(long contentLength) {
    if (contentLength < 0 || contentLength > limits.maxUploadSize()) {
      throw new IllegalArgumentException(
          "contentLength must be between 0 and max-upload-size=" + limits.maxUploadSize());
    }
  }

  private Void put(URI url, PresignedUploadTicket ticket, UploadContent body) {
    return clients
        .upload()
        .put()
        .uri(url)
        .headers(headers -> signedHeaders(headers, ticket, body.length()))
        .body(body)
        .exchange((req, res) -> requireStored(res));
  }

  private static void signedHeaders(
      HttpHeaders headers, PresignedUploadTicket ticket, long contentLength) {
    ticket.requiredHeaders().forEach(headers::set);
    headers.setContentLength(contentLength);
  }

  private static Void requireStored(ClientHttpResponse response) throws IOException {
    int status = response.getStatusCode().value();
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw ErrorResponses.uploadRejected(status);
    }
    return null;
  }

  private DocumentHttpResponse post(UploadRequest request, PresignedUrlHttpRequest body) {
    return clients
        .api()
        .post()
        .uri(UPLOAD_PATH, request.referenceId(), request.workflowId())
        .body(body)
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> fail(res))
        .body(DocumentHttpResponse.class);
  }

  private DocumentHttpResponse fetchDocument(String referenceId, UUID fileId) {
    return clients
        .api()
        .get()
        .uri(DOCUMENT_PATH, referenceId, fileId)
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> fail(res))
        .body(DocumentHttpResponse.class);
  }

  private <T> T exchangeContent(String referenceId, UUID fileId, BodyReader<T> reader) {
    return clients
        .content()
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
    byte[] content = body.readNBytes((int) (limits.maxDownloadSize() + 1));
    if (content.length > limits.maxDownloadSize()) {
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
    } catch (UploadContent.ReadException e) {
      throw new UncheckedIOException("Could not read the upload content", e.getCause());
    } catch (UploadContent.ShortContentException e) {
      throw new IllegalArgumentException(e.getMessage(), e);
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
