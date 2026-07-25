package io.codehunters.fileshare.application;

import io.codehunters.fileshare.domain.model.FileRef;
import io.codehunters.fileshare.domain.model.PresignedDownloadTicket;
import io.codehunters.fileshare.domain.model.PresignedUploadTicket;
import java.util.UUID;

/**
 * Client port for the file-share service. Consumers depend on this interface; the SDK supplies a
 * REST-backed implementation through auto-configuration.
 *
 * <p>All operations translate non-2xx responses and transport failures into the {@link
 * io.codehunters.fileshare.domain.exception.FileShareException} hierarchy.
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
   * Requests a presigned URL to download an existing, completed file.
   *
   * @param referenceId the business reference the file belongs to
   * @param fileId the file identifier
   * @return a ticket holding the {@link FileRef} and the presigned download URL
   * @throws io.codehunters.fileshare.domain.exception.FileShareConflictException if the file is not
   *     in a downloadable state
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
   * Downloads the file content through the server-side content endpoint, bypassing presigned URLs.
   *
   * @param referenceId the business reference the file belongs to
   * @param fileId the file identifier
   * @return the raw file bytes
   */
  byte[] download(String referenceId, UUID fileId);
}
