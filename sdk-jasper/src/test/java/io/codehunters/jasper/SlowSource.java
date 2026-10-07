package io.codehunters.jasper;

import java.time.Duration;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.data.JRAbstractBeanDataSource;

/** Endless rows that each take a while to arrive, for timing a fill out. */
final class SlowSource extends JRAbstractBeanDataSource {

  private final Duration perRow;
  private int row;

  SlowSource(Duration perRow) {
    super(true);
    this.perRow = perRow;
  }

  @Override
  public boolean next() {
    try {
      Thread.sleep(perRow);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
    row++;
    return true;
  }

  @Override
  public Object getFieldValue(JRField field) {
    return "name".equals(field.getName()) ? "row" + row : row;
  }

  @Override
  public void moveFirst() {
    row = 0;
  }
}
