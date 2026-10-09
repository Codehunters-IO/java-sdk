package io.codehunters.fileshare;

import io.codehunters.fileshare.model.FileRef;
import io.codehunters.fileshare.model.PresignedDownloadTicket;
import io.codehunters.fileshare.model.PresignedUploadTicket;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Client port for the file-share service. Consumers depend on this interface; the SDK supplies a
 * REST-backed implementation through auto-configuration.
 *
 * <p>All operations translate non-2xx responses and transport failures into the {@link
 * io.codehunters.fileshare.FileShareException} hierarchy, including responses the client cannot
 * read. A {@code referenceId} that is not a safe path segment is rejected with an {@link
 * IllegalArgumentException} before any call is made.
 */
public interface FileShareClient {

  /**
   * Requests a presigned URL to upload a new file.
   *
   * @param request the file metadata and target reference/workflow
   * @return a ticket holding the created {@link FileRef} and the presigned upload URL
   */
  PresignedUploadTicket requestUpload(UploadRequest request);

  /**
   * Sends the content of a file to the presigned URL of its upload ticket: a {@code PUT} with the
   * ticket's {@link PresignedUploadTicket#requiredHeaders() required headers} and exactly {@code
   * contentLength} bytes. The request never carries the service token and never follows a redirect.
   * {@code content} is neither closed nor read past {@code contentLength} bytes.
   *
   * @param ticket the ticket returned by {@link #requestUpload(UploadRequest)}
   * @param content the bytes to send
   * @param contentLength how many bytes to send; the size declared in the upload request
   * @throws IllegalArgumentException if {@code contentLength} is negative or above {@code
   *     codehunters.file-share.max-upload-size}, or {@code content} ends before it
   * @throws java.io.UncheckedIOException if reading {@code content} fails
   * @throws io.codehunters.fileshare.FileShareUnauthorizedException if object storage rejects the
   *     signature, typically because the ticket expired
   */
  void upload(PresignedUploadTicket ticket, InputStream content, long contentLength);

  /**
   * Requests a presigned URL to download an existing, completed file.
   *
   * @param referenceId the business reference the file belongs to
   * @param fileId the file identifier
   * @return a ticket holding the {@link FileRef} and the presigned download URL
   * @throws io.codehunters.fileshare.FileShareConflictException if the file is not in a
   *     downloadable state
   */
  PresignedDownloadTicket requestDownload(String referenceId, UUID fileId);

  /**
   * Fetches the metadata of a file without downloading its content.
   *
   * @param referenceId the business reference the file belongs to
   * @param fileId the file identifier
   * @return the current {@link FileRef}
   */
  FileRef getMetadata(String referenceId, UUID fileId);

  /**
   * Downloads the file content into memory through the server-side content endpoint, bypassing
   * presigned URLs. Content larger than {@code codehunters.file-share.max-download-size} fails;
   * stream large files with {@link #download(String, UUID, OutputStream)}.
   *
   * @param referenceId the business reference the file belongs to
   * @param fileId the file identifier
   * @return the raw file bytes
   */
  byte[] download(String referenceId, UUID fileId);

  /**
   * Streams the file content into {@code target} through the server-side content endpoint. The
   * target is neither flushed nor closed.
   *
   * @param referenceId the business reference the file belongs to
   * @param fileId the file identifier
   * @param target where the bytes are written
   * @return the number of bytes written
   */
  long download(String referenceId, UUID fileId, OutputStream target);

  /**
   * Streams the content of {@code file} into {@code target}.
   *
   * @param file the file to download
   * @param target where the bytes are written
   * @return the number of bytes written
   */
  default long download(FileRef file, OutputStream target) {
    return download(file.referenceId(), file.id(), target);
  }
}
