package io.codehunters.audit;

import java.util.Map;

class OrderService {

  @Auditable(
      entityType = "ORDER",
      action = "CREATE",
      entityId = "#result.get('id')",
      sourceService = "order-service",
      payload = {"amount=#command.get('amount')", "customer=#command.get('customer')"})
  Map<String, Object> createOrder(Map<String, Object> command) {
    return Map.of("id", "ORD-001", "status", "CREATED");
  }

  @Auditable(entityType = "ORDER", action = "READ", entityId = "#orderId")
  Map<String, Object> findOrder(String orderId) {
    return Map.of("id", orderId, "status", "ACTIVE");
  }

  @Auditable(entityType = "ORDER", action = "DELETE", entityId = "#orderId")
  void deleteOrder(String orderId) {
    throw new IllegalStateException("Order cannot be deleted");
  }

  @Auditable(
      entityType = "ORDER",
      action = "UPDATE",
      entityId = "#orderId",
      actorId = "#actorOverride",
      actorType = "SYSTEM")
  void updateOrderStatus(String orderId, String newStatus, String actorOverride) {
    // no-op
  }

  @Auditable(
      entityType = "ORDER",
      action = "CREATE",
      entityId = "#result.get('id')",
      sourceService = "order-service")
  Map<String, Object> createOrderFailing(Map<String, Object> command) {
    throw new RuntimeException("DB connection failed");
  }

  @Auditable(entityType = "ORDER", action = "SHIP", entityId = "#orderId", correlationId = "#ref")
  void shipOrder(String orderId, String ref) {
    // no-op
  }

  @Auditable(entityType = "ORDER", action = "ARCHIVE", entityId = "#orderId")
  void archiveOrder(String orderId) {
    // void method — no result
  }
}
