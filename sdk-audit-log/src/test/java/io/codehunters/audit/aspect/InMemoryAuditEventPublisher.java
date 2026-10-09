package io.codehunters.audit.aspect;

import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditEventPublisherPort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryAuditEventPublisher implements AuditEventPublisherPort {

  private final List<AuditEvent> events = new ArrayList<>();

  @Override
  public void publish(AuditEvent event) {
    events.add(event);
  }

  List<AuditEvent> getEvents() {
    return Collections.unmodifiableList(events);
  }

  public AuditEvent getLastEvent() {
    return events.getLast();
  }

  void clear() {
    events.clear();
  }
}
