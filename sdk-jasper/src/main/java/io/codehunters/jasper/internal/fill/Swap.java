package io.codehunters.jasper.internal.fill;

import io.codehunters.jasper.Virtualization;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.util.JRSwapFile;

/**
 * One render's page swap file, in its own folder, or nothing when virtualization is off. Closing it
 * deletes the file and the folder, so it must outlive the export, which reads the swapped pages
 * back.
 */
public final class Swap implements AutoCloseable {

  private static final int BLOCK_SIZE = 4096;
  private static final int MIN_GROW = 64;

  private final JRSwapFileVirtualizer virtualizer;
  private final Path folder;

  private Swap(JRSwapFileVirtualizer virtualizer, Path folder) {
    this.virtualizer = virtualizer;
    this.folder = folder;
  }

  public static Swap open(
      Virtualization virtualization, JasperReportsContext context, SwapFolder swaps)
      throws IOException {
    if (!virtualization.enabled()) {
      return new Swap(null, null);
    }
    Path folder = swaps.newRenderFolder();
    var file = new JRSwapFile(context, folder.toString(), BLOCK_SIZE, MIN_GROW);
    return new Swap(new JRSwapFileVirtualizer(virtualization.maxPages(), file, true), folder);
  }

  public void attachTo(Map<String, Object> parameters) {
    if (virtualizer != null) {
      parameters.put(JRParameter.REPORT_VIRTUALIZER, virtualizer);
    }
  }

  public void freeze() {
    if (virtualizer != null) {
      virtualizer.setReadOnly(true);
    }
  }

  @Override
  public void close() {
    if (virtualizer != null) {
      virtualizer.cleanup();
      SwapFolder.delete(folder);
    }
  }
}
