package io.codehunters.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeoValidationDecisionTest {
  @Test
  void allowed_carries_matched_zone() {
    var d =
        GeoValidationDecision.allowed(
            "05001", "Medellin", MatchedZoneType.MUNICIPALITY, "Antioquia");
    assertTrue(d.allowed());
    assertEquals("05001", d.zoneCode());
    assertEquals(MatchedZoneType.MUNICIPALITY, d.zoneType());
    assertNull(d.denialReason());
  }

  @Test
  void denied_has_reason_and_no_zone() {
    var d = GeoValidationDecision.denied();
    assertFalse(d.allowed());
    assertNull(d.zoneCode());
    assertEquals(DenialReason.OUTSIDE_ALLOWED_ZONE, d.denialReason());
  }
}
