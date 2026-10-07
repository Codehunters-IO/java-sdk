package io.codehunters.audit;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Audited methods whose work finishes after they return. */
class AsyncOrderService {

  private final CompletableFuture<Map<String, Object>> pending;
  private final Mono<Map<String, Object>> mono;
  private final Flux<String> flux;

  AsyncOrderService(
      CompletableFuture<Map<String, Object>> pending,
      Mono<Map<String, Object>> mono,
      Flux<String> flux) {
    this.pending = pending;
    this.mono = mono;
    this.flux = flux;
  }

  @Auditable(entityType = "ORDER", action = "PLACE", entityId = "#result?.get('id')")
  CompletableFuture<Map<String, Object>> placeOrder(String orderId) {
    return pending;
  }

  @Auditable(entityType = "ORDER", action = "PLACE", entityId = "#orderId")
  CompletionStage<Map<String, Object>> placeOrderStage(String orderId) {
    return pending;
  }

  @Auditable(entityType = "ORDER", action = "PLACE", entityId = "#orderId")
  CompletableFuture<Map<String, Object>> placeNothing(String orderId) {
    return null;
  }

  @Auditable(entityType = "ORDER", action = "READ", entityId = "#result?.get('id')")
  Mono<Map<String, Object>> readOrder(String orderId) {
    return mono;
  }

  @Auditable(entityType = "ORDER", action = "LIST", entityId = "#customerId")
  Flux<String> listOrders(String customerId) {
    return flux;
  }

  @Auditable(entityType = "ORDER", action = "PLACE", entityId = "#command['id']")
  CompletableFuture<Map<String, Object>> placeFor(Map<String, Object> command) {
    return pending;
  }

  @Auditable(entityType = "ORDER", action = "PUBLISH", entityId = "#customerId")
  ConnectableFlux<String> publishOrders(String customerId) {
    return flux.publish();
  }
}
