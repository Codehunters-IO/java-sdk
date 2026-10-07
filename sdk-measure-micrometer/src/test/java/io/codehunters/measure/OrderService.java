package io.codehunters.measure;

import java.util.concurrent.CompletableFuture;

/** Methods measured with every {@link Measured} option the aspect supports. */
class OrderService {

  @Measured(
      name = "orders.place",
      tags = {"channel", "web"})
  String place(String orderId) {
    return "placed-" + orderId;
  }

  @Measured
  String byDefaultName() {
    return "ok";
  }

  @Measured(name = "orders.fail")
  void fail() {
    throw new IllegalStateException("card 4111 declined");
  }

  @Measured(name = "orders.fail.quiet", recordExceptions = false)
  void failQuietly() {
    throw new IllegalStateException("quiet");
  }

  @Measured(
      name = "orders.by.customer",
      expressions = {"customer=#customerId"})
  void byCustomer(String customerId) {}

  @Measured(
      name = "orders.by.position",
      expressions = {"customer=#p0"})
  void byPosition(String customerId) {}

  @Measured(
      name = "orders.missing.method",
      expressions = {"customer=#customerId.noSuchMethod()"})
  void missingMethod(String customerId) {}

  @Measured(
      name = "orders.unparseable",
      expressions = {"customer=#customerId.("})
  void unparseable(String customerId) {}

  @Measured(
      name = "orders.type.reference",
      expressions = {"cpus=T(java.lang.Runtime).getRuntime().availableProcessors()"})
  void typeReference() {}

  @Measured(
      name = "orders.instance.method",
      expressions = {"customer=#customerId.toUpperCase()"})
  void instanceMethod(String customerId) {}

  @Measured(
      name = "orders.blank.static",
      tags = {"region", ""})
  void blankStaticTag() {}

  @Measured(name = "orders.counted", count = true, countValue = 2.5)
  String counted() {
    return "ok";
  }

  @Measured(name = "orders.conditional", count = true, countCondition = "#result == 'OK'")
  String conditional(String status) {
    return status;
  }

  @Measured(name = "orders.bad.condition", count = true, countCondition = "#result.noSuchMethod()")
  String badCondition() {
    return "ok";
  }

  @Measured(name = "orders.long", longTask = true)
  void longOperation(Runnable inside) {
    inside.run();
  }

  @Measured(name = "orders.async", count = true)
  CompletableFuture<String> async(CompletableFuture<String> pending) {
    return pending;
  }
}
