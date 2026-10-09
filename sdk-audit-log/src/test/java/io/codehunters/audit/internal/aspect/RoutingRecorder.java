package io.codehunters.audit.internal.aspect;

import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditEventPublisherPort;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import reactor.core.scheduler.Schedulers;

/** Records which entry point received each event, and the thread state it was published with. */
class RoutingRecorder implements AuditEventPublisherPort {

  static final String TRACE_ID = "traceId";

  private final List<AuditEvent> attached = new CopyOnWriteArrayList<>();
  private final List<AuditEvent> detached = new CopyOnWriteArrayList<>();
  private final BlockingQueue<Observation> observations = new LinkedBlockingQueue<>();

  @Override
  public void publish(AuditEvent event) {
    attached.add(event);
    observe(event);
  }

  @Override
  public void publishDetached(AuditEvent event) {
    detached.add(event);
    observe(event);
  }

  List<AuditEvent> attached() {
    return attached;
  }

  List<AuditEvent> detached() {
    return detached;
  }

  Observation next() throws InterruptedException {
    return observations.poll(5, TimeUnit.SECONDS);
  }

  private void observe(AuditEvent event) {
    observations.add(new Observation(event, MDC.get(TRACE_ID), Schedulers.isInNonBlockingThread()));
  }

  /** An event and the state of the thread that published it. */
  record Observation(AuditEvent event, String traceId, boolean nonBlockingThread) {}
}
