package io.codehunters.audit.internal.aspect;

import static org.junit.jupiter.api.Assertions.*;

import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditEventPublisherPort;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class AuditAspectTest {

  @Autowired private OrderService orderService;

  @Autowired private AuditEventPublisherPort publisher;

  private InMemoryAuditEventPublisher inMemory() {
    return (InMemoryAuditEventPublisher) publisher;
  }

  @BeforeEach
  void setUp() {
    inMemory().clear();
  }

  @Test
  void should_publish_audit_event_when_method_succeeds() {
    var command = Map.<String, Object>of("amount", new BigDecimal("99.99"), "customer", "CUST-01");

    orderService.createOrder(command);

    AuditEvent event = inMemory().getLastEvent();
    assertEquals("ORDER", event.entityType());
    assertEquals("CREATE", event.action());
    assertEquals("ORD-001", event.entityId());
    assertEquals("SUCCESS", event.status());
    assertEquals("order-service", event.sourceService());
    assertNotNull(event.createdAt());
    assertTrue(event.success());
    assertNull(event.errorMessage());
    assertNotNull(event.durationMs());
  }

  @Test
  void should_resolve_payload_from_spel_expressions() {
    var command = Map.<String, Object>of("amount", new BigDecimal("99.99"), "customer", "CUST-01");

    orderService.createOrder(command);

    AuditEvent event = inMemory().getLastEvent();
    assertEquals("CUST-01", event.payload().get("customer"));
    assertEquals(new BigDecimal("99.99"), event.payload().get("amount"));
  }

  @Test
  void should_resolve_entity_id_from_parameter() {
    orderService.findOrder("ORD-555");

    AuditEvent event = inMemory().getLastEvent();
    assertEquals("ORD-555", event.entityId());
    assertEquals("READ", event.action());
  }

  @Test
  void should_publish_error_event_when_method_throws() {
    assertThrows(IllegalStateException.class, () -> orderService.deleteOrder("ORD-999"));

    assertFalse(inMemory().getEvents().isEmpty());
    AuditEvent event = inMemory().getLastEvent();
    assertEquals("ERROR", event.status());
    assertFalse(event.success());
    assertNotNull(event.errorMessage());
    assertNotNull(event.durationMs());
  }

  @Test
  void should_use_context_provider_for_actor_and_ip() {
    orderService.findOrder("ORD-100");

    AuditEvent event = inMemory().getLastEvent();
    assertEquals("user-123", event.actorId());
    assertEquals("USER", event.actorType());
    assertEquals("192.168.1.1", event.ipAddress());
  }

  @Test
  void should_override_actor_from_annotation_spel() {
    orderService.updateOrderStatus("ORD-200", "SHIPPED", "admin-456");

    AuditEvent event = inMemory().getLastEvent();
    assertEquals("admin-456", event.actorId());
    assertEquals("SYSTEM", event.actorType());
  }

  @Test
  void should_fallback_source_service_from_context_provider() {
    orderService.findOrder("ORD-300");

    AuditEvent event = inMemory().getLastEvent();
    assertEquals("test-service", event.sourceService());
  }

  @Test
  void should_publish_audit_event_when_method_with_result_spel_throws_exception() {
    var command = Map.<String, Object>of("amount", new BigDecimal("50.00"));

    assertThrows(RuntimeException.class, () -> orderService.createOrderFailing(command));

    assertFalse(
        inMemory().getEvents().isEmpty(), "Audit event must be published even on exception");
    AuditEvent event = inMemory().getLastEvent();
    assertEquals("ORDER", event.entityType());
    assertEquals("CREATE", event.action());
    assertEquals("ERROR", event.status());
    assertFalse(event.success());
    assertNull(event.entityId(), "entityId should be null when #result is unavailable");
    assertEquals("java.lang.RuntimeException", event.errorMessage());
  }

  @Test
  void should_resolve_context_provider_http_fields() {
    orderService.findOrder("ORD-400");

    AuditEvent event = inMemory().getLastEvent();
    assertEquals("/api/orders", event.resource());
    assertEquals("POST", event.httpMethod());
    assertEquals("TestAgent/1.0", event.userAgent());
  }

  @Test
  void should_not_duplicate_entity_id_as_resource_id() {
    var command = Map.<String, Object>of("amount", new BigDecimal("10.00"), "customer", "CUST-02");

    orderService.createOrder(command);

    AuditEvent event = inMemory().getLastEvent();
    assertEquals("ORD-001", event.entityId());
    assertNull(event.resourceId(), "resourceId should not duplicate entityId");
  }

  @Test
  void should_publish_audit_event_for_void_method() {
    orderService.archiveOrder("ORD-600");

    assertFalse(inMemory().getEvents().isEmpty());
    AuditEvent event = inMemory().getLastEvent();
    assertEquals("ORDER", event.entityType());
    assertEquals("ARCHIVE", event.action());
    assertEquals("ORD-600", event.entityId());
    assertEquals("SUCCESS", event.status());
    assertTrue(event.success());
  }

  @Test
  void should_record_exception_class_not_message_when_method_throws() {
    assertThrows(IllegalStateException.class, () -> orderService.deleteOrder("ORD-999"));

    assertEquals("java.lang.IllegalStateException", inMemory().getLastEvent().errorMessage());
  }

  @Test
  void should_resolve_correlation_id_when_expression_is_a_uuid() {
    var ref = UUID.fromString("4bf92f35-77b3-4da6-a3ce-929d0e0e4736");

    orderService.shipOrder("ORD-700", ref.toString());

    assertEquals(ref, inMemory().getLastEvent().correlationId());
  }

  @Test
  void should_publish_event_with_null_correlation_id_when_expression_is_not_a_uuid() {
    orderService.shipOrder("ORD-701", "not-a-uuid");

    AuditEvent event = inMemory().getLastEvent();
    assertEquals("ORD-701", event.entityId());
    assertNull(event.correlationId());
  }

  @Test
  void should_stamp_created_at_from_the_injected_clock() {
    orderService.findOrder("ORD-800");

    assertEquals(TestConfig.NOW.atOffset(ZoneOffset.UTC), inMemory().getLastEvent().createdAt());
  }
}
