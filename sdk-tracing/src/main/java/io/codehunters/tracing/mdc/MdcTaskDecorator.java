package io.codehunters.tracing.mdc;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/** Propagates the current {@link MDC} context to a {@link Runnable} executed on another thread. */
public class MdcTaskDecorator implements TaskDecorator {

  /** Creates the decorator. */
  public MdcTaskDecorator() {}

  @Override
  public Runnable decorate(Runnable runnable) {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    return () -> {
      Map<String, String> previous = MDC.getCopyOfContextMap();
      try {
        if (contextMap == null) {
          MDC.clear();
        } else {
          MDC.setContextMap(contextMap);
        }
        runnable.run();
      } finally {
        if (previous == null) {
          MDC.clear();
        } else {
          MDC.setContextMap(previous);
        }
      }
    };
  }
}
