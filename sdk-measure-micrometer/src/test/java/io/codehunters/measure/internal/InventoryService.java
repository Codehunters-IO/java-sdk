package io.codehunters.measure.internal;

import io.codehunters.measure.Measured;

/** Measured at class level, with one method that overrides the class annotation. */
@Measured(name = "inventory")
class InventoryService {

  String reserve(String sku) {
    return "reserved-" + sku;
  }

  @Measured(name = "inventory.release")
  void release(String sku) {}
}
