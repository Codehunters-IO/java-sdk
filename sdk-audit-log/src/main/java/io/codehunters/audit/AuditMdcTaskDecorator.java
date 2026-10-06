package io.codehunters.audit;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Runs audit tasks with the submitting thread's {@link MDC}, so trace and correlation ids reach the
 * worker's log lines, and restores the worker's own MDC afterwards.
 */
final class AuditMdcTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    Map<String, String> submitted = MDC.getCopyOfContextMap();
    return () -> {
      Map<String, String> previous = MDC.getCopyOfContextMap();
      replaceMdc(submitted);
      try {
        runnable.run();
      } finally {
        replaceMdc(previous);
      }
    };
  }

  private static void replaceMdc(Map<String, String> context) {
    if (context == null) {
      MDC.clear();
    } else {
      MDC.setContextMap(context);
    }
  }
}
