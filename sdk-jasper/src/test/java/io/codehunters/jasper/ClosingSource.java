package io.codehunters.jasper;

import net.sf.jasperreports.engine.JREmptyDataSource;

/** One empty row from a source that records whether the renderer closed it. */
final class ClosingSource extends JREmptyDataSource implements AutoCloseable {

  private boolean closed;

  ClosingSource() {
    super(1);
  }

  @Override
  public void close() {
    closed = true;
  }

  boolean isClosed() {
    return closed;
  }
}
