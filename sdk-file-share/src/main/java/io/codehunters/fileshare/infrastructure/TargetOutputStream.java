package io.codehunters.fileshare.infrastructure;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Marks write failures of the caller's stream, so they are not mistaken for a file-share outage.
 */
final class TargetOutputStream extends FilterOutputStream {

  TargetOutputStream(OutputStream target) {
    super(target);
  }

  @Override
  public void write(int b) {
    try {
      out.write(b);
    } catch (IOException e) {
      throw new WriteException(e);
    }
  }

  @Override
  public void write(byte[] bytes, int offset, int length) {
    try {
      out.write(bytes, offset, length);
    } catch (IOException e) {
      throw new WriteException(e);
    }
  }

  /** A write to the caller's stream failed. */
  static final class WriteException extends RuntimeException {

    WriteException(IOException cause) {
      super(cause);
    }

    @Override
    public synchronized IOException getCause() {
      return (IOException) super.getCause();
    }
  }
}
