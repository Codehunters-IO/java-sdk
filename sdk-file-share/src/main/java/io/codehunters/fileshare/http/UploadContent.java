package io.codehunters.fileshare.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.springframework.http.StreamingHttpOutputMessage;

/**
 * Streams exactly {@code length} bytes of the caller's content. Failures of the caller's stream are
 * marked, so they are not mistaken for an object-storage outage.
 */
final class UploadContent implements StreamingHttpOutputMessage.Body {

  private static final int BUFFER_SIZE = 8192;

  private final InputStream source;
  private final long length;

  UploadContent(InputStream source, long length) {
    this.source = source;
    this.length = length;
  }

  long length() {
    return length;
  }

  @Override
  public void writeTo(OutputStream target) throws IOException {
    byte[] buffer = new byte[BUFFER_SIZE];
    long remaining = length;
    while (remaining > 0) {
      int read = read(buffer, (int) Math.min(buffer.length, remaining));
      target.write(buffer, 0, read);
      remaining -= read;
    }
  }

  private int read(byte[] buffer, int max) {
    try {
      int read = source.read(buffer, 0, max);
      if (read < 0) {
        throw new ShortContentException(length);
      }
      return read;
    } catch (IOException e) {
      throw new ReadException(e);
    }
  }

  /** A read from the caller's stream failed. */
  static final class ReadException extends RuntimeException {

    ReadException(IOException cause) {
      super(cause);
    }

    @Override
    public synchronized IOException getCause() {
      return (IOException) super.getCause();
    }
  }

  /** The caller's stream ended before {@code length} bytes. */
  static final class ShortContentException extends RuntimeException {

    ShortContentException(long length) {
      super("upload content ended before contentLength=" + length + " bytes");
    }
  }
}
