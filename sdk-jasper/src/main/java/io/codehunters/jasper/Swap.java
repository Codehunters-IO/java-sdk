package io.codehunters.jasper;

import java.util.Map;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.util.JRSwapFile;

/**
 * One render's page swap file, or nothing when virtualization is off. Closing it deletes the file,
 * so it must outlive the export, which reads the swapped pages back.
 */
final class Swap implements AutoCloseable {

  private static final int BLOCK_SIZE = 4096;
  private static final int MIN_GROW = 64;

  private final JRSwapFileVirtualizer virtualizer;

  private Swap(JRSwapFileVirtualizer virtualizer) {
    this.virtualizer = virtualizer;
  }

  static Swap open(Virtualization virtualization, JasperReportsContext context) {
    if (!virtualization.enabled()) {
      return new Swap(null);
    }
    var file = new JRSwapFile(context, virtualization.directory().toString(), BLOCK_SIZE, MIN_GROW);
    return new Swap(new JRSwapFileVirtualizer(virtualization.maxPages(), file, true));
  }

  void attachTo(Map<String, Object> parameters) {
    if (virtualizer != null) {
      parameters.put(JRParameter.REPORT_VIRTUALIZER, virtualizer);
    }
  }

  void freeze() {
    if (virtualizer != null) {
      virtualizer.setReadOnly(true);
    }
  }

  @Override
  public void close() {
    if (virtualizer != null) {
      virtualizer.cleanup();
    }
  }
}
