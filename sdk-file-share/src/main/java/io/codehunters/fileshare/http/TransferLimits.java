package io.codehunters.fileshare.http;

/**
 * Size limits of content transfers.
 *
 * @param maxDownloadSize the largest content, in bytes, {@code download(referenceId, fileId)}
 *     buffers in memory, at most {@link #MAX_DOWNLOAD_SIZE_LIMIT}
 * @param maxUploadSize the largest content, in bytes, {@code upload(ticket, content,
 *     contentLength)} sends
 */
public record TransferLimits(long maxDownloadSize, long maxUploadSize) {

  /**
   * The largest {@code max-download-size}: one byte less than the biggest array the JVM reliably
   * allocates, since one extra byte is read to detect oversized content.
   */
  public static final long MAX_DOWNLOAD_SIZE_LIMIT = Integer.MAX_VALUE - 9;

  /**
   * Rejects limits that cannot be applied.
   *
   * @throws IllegalArgumentException if {@code maxDownloadSize} is not positive or exceeds {@link
   *     #MAX_DOWNLOAD_SIZE_LIMIT}, or {@code maxUploadSize} is not positive
   */
  public TransferLimits {
    if (maxDownloadSize <= 0 || maxDownloadSize > MAX_DOWNLOAD_SIZE_LIMIT) {
      throw new IllegalArgumentException(
          "max-download-size must be between 1 byte and " + MAX_DOWNLOAD_SIZE_LIMIT + " bytes");
    }
    if (maxUploadSize <= 0) {
      throw new IllegalArgumentException("max-upload-size must be at least 1 byte");
    }
  }
}
