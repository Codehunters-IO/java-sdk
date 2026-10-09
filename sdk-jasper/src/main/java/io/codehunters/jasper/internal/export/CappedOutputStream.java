package io.codehunters.jasper.internal.export;

import io.codehunters.jasper.ReportException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

/** Buffers a document in memory and fails as soon as it grows past the limit. */
public final class CappedOutputStream extends OutputStream {

  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
  private final long limit;

  public CappedOutputStream(long limit) {
    this.limit = limit;
  }

  @Override
  public void write(int b) {
    ensureRoom(1);
    buffer.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) {
    ensureRoom(len);
    buffer.write(b, off, len);
  }

  public byte[] toByteArray() {
    return buffer.toByteArray();
  }

  private void ensureRoom(int length) {
    if (buffer.size() + (long) length > limit) {
      throw new ReportException("the report exceeds the output limit of " + limit + " bytes");
    }
  }
}
