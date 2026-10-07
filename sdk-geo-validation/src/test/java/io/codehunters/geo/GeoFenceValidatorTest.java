package io.codehunters.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GeoFenceValidatorTest {

  private static final ZoneRef SCOPE = new ZoneRef("campaign-42");
  private static final GeoPoint POINT = GeoPoint.of(6.25, -75.56);

  private final AtomicInteger lookups = new AtomicInteger();
  private final GeoFenceValidator validator =
      (scope, point) -> {
        lookups.incrementAndGet();
        return GeoDecision.denied(DenialReason.OUTSIDE_ZONE);
      };

  @Test
  void should_allow_without_a_lookup_when_coverage_is_unrestricted() {
    GeoDecision decision = validator.validate(SCOPE, POINT, Coverage.UNRESTRICTED);

    assertThat(decision).isEqualTo(GeoDecision.unrestricted());
    assertThat(lookups).hasValue(0);
  }

  @Test
  void should_look_the_point_up_when_coverage_is_restricted() {
    GeoDecision decision = validator.validate(SCOPE, POINT, Coverage.RESTRICTED);

    assertThat(decision.denialReason()).contains(DenialReason.OUTSIDE_ZONE);
    assertThat(lookups).hasValue(1);
  }
}
