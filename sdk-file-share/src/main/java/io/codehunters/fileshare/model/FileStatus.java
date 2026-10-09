package io.codehunters.fileshare.model;

/** Lifecycle status of a file managed by the file-share service. */
public enum FileStatus {
  /** Metadata registered, no upload requested yet. */
  CREATED,
  /** A presigned upload URL was issued; content not yet uploaded. */
  PENDING_UPLOAD,
  /** Content upload is in progress. */
  UPLOADING,
  /** Content fully uploaded and available for download. */
  COMPLETED,
  /** Upload or processing failed. */
  ERROR,
  /** File has been deleted. */
  DELETED,
  /** The service sent no status, or one this SDK version does not know. */
  UNKNOWN
}
