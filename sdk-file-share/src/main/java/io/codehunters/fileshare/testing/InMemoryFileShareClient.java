package io.codehunters.fileshare.testing;

import io.codehunters.fileshare.FileShareClient;
import io.codehunters.fileshare.FileShareConflictException;
import io.codehunters.fileshare.FileShareNotFoundException;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link FileShareClient} that keeps files in memory, for tests of code that uses the SDK. Upload
 * tickets point at {@code https://file-share.test}, a host nothing answers on; {@link
 * #upload(PresignedUploadTicket, InputStream, long)} stores the content and completes the file.
 *
 * <p>Test-only: it is never auto-configured. It is thread-safe.
 */
public final class InMemoryFileShareClient implements FileShareClient {

  private static final Duration TICKET_LIFETIME = Duration.ofMinutes(15);
  private static final String HOST = "https://file-share.test/";

  private final Clock clock;
  private final Map<UUID, StoredFile> files = new ConcurrentHashMap<>();

  /** Creates an empty store whose tickets expire 15 minutes after the UTC system time. */
  public InMemoryFileShareClient() {
    this(Clock.systemUTC());
  }

  /**
   * Creates an empty store.
   *
   * @param clock the clock ticket expiries are computed from
   */
  public InMemoryFileShareClient(Clock clock) {
    this.clock = clock;
  }

  /**
   * Adds a completed file, as if it had been uploaded.
   *
   * @param request the file metadata
   * @param content the file content
   * @return the stored file
   */
  public FileRef store(UploadRequest request, byte[] content) {
    var file = toFileRef(UUID.randomUUID(), request, FileStatus.COMPLETED, content.length);
    files.put(file.id(), new StoredFile(file, content.clone()));
    return file;
  }

  /**
   * Returns the content of a file, when it has any.
   *
   * @param fileId the file identifier
   * @return a copy of the content; empty for an unknown file or one still waiting for its content
   */
  public Optional<byte[]> content(UUID fileId) {
    return Optional.ofNullable(files.get(fileId))
        .map(StoredFile::content)
        .map(content -> content.clone());
  }

  @Override
  public PresignedUploadTicket requestUpload(UploadRequest request) {
    var file = toFileRef(UUID.randomUUID(), request, FileStatus.PENDING_UPLOAD, request.size());
    files.put(file.id(), new StoredFile(file, null));
    return new PresignedUploadTicket(file, url("upload", file.id()), expiry());
  }

  @Override
  public void upload(PresignedUploadTicket ticket, InputStream content, long contentLength) {
    var pending = find(ticket.file().referenceId(), ticket.file().id()).file();
    byte[] bytes = readExactly(content, contentLength);
    var completed = withStatus(pending, FileStatus.COMPLETED, bytes.length);
    files.put(completed.id(), new StoredFile(completed, bytes));
  }

  @Override
  public PresignedDownloadTicket requestDownload(String referenceId, UUID fileId) {
    var file = completed(referenceId, fileId).file();
    return new PresignedDownloadTicket(file, url("download", fileId), expiry());
  }

  @Override
  public FileRef getMetadata(String referenceId, UUID fileId) {
    return find(referenceId, fileId).file();
  }

  @Override
  public byte[] download(String referenceId, UUID fileId) {
    return completed(referenceId, fileId).content().clone();
  }

  @Override
  public long download(String referenceId, UUID fileId, OutputStream target) {
    byte[] content = download(referenceId, fileId);
    try {
      target.write(content);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not write file-share content to the target", e);
    }
    return content.length;
  }

  private StoredFile find(String referenceId, UUID fileId) {
    FileConstraints.requireReferenceId(referenceId);
    var stored = files.get(fileId);
    if (stored == null || !stored.file().referenceId().equals(referenceId)) {
      throw new FileShareNotFoundException("No file " + fileId + " under " + referenceId);
    }
    return stored;
  }

  private StoredFile completed(String referenceId, UUID fileId) {
    var stored = find(referenceId, fileId);
    if (stored.content() == null) {
      throw new FileShareConflictException("File " + fileId + " has no content yet");
    }
    return stored;
  }

  private static byte[] readExactly(InputStream content, long contentLength) {
    try {
      byte[] bytes = content.readNBytes(Math.toIntExact(contentLength));
      if (bytes.length < contentLength) {
        throw new IllegalArgumentException(
            "upload content ended before contentLength=" + contentLength + " bytes");
      }
      return bytes;
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the upload content", e);
    }
  }

  private static FileRef toFileRef(UUID id, UploadRequest request, FileStatus status, long size) {
    return new FileRef(
        id,
        request.referenceId(),
        request.workflowId(),
        request.filename(),
        request.contentType(),
        size,
        status,
        request.metadata());
  }

  private static FileRef withStatus(FileRef file, FileStatus status, long size) {
    return new FileRef(
        file.id(),
        file.referenceId(),
        file.workflowId(),
        file.filename(),
        file.contentType(),
        size,
        status,
        file.metadata());
  }

  private static URI url(String operation, UUID fileId) {
    return URI.create(HOST + operation + "/" + fileId);
  }

  private Instant expiry() {
    return clock.instant().plus(TICKET_LIFETIME);
  }

  private record StoredFile(FileRef file, byte[] content) {}
}
