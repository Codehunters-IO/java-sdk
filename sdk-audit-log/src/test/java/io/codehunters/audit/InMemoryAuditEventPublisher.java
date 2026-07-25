package io.codehunters.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class InMemoryAuditEventPublisher implements AuditEventPublisherPort {

  private final List<AuditEvent> events = new ArrayList<>();

  @Override
  public void publish(AuditEvent event) {
    events.add(event);
  }

  List<AuditEvent> getEvents() {
    return Collections.unmodifiableList(events);
  }

  AuditEvent getLastEvent() {
    return events.getLast();
  }

  void clear() {
    events.clear();
  }
}
