package io.codehunters.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeoDecisionTest {

  private static final MatchedZone ZONE = MatchedZone.of("05001", "CITY", "Medellin", "Antioquia");

  @Test
  void should_carry_the_matched_zone_when_allowed() {
    GeoDecision decision = GeoDecision.allowed(ZONE);

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.zone()).contains(ZONE);
    assertThat(decision.denialReason()).isEmpty();
  }

  @Test
  void should_carry_no_zone_when_unrestricted() {
    GeoDecision decision = GeoDecision.unrestricted();

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.zone()).isEmpty();
    assertThat(decision.denialReason()).isEmpty();
  }

  @Test
  void should_carry_the_reason_and_no_zone_when_denied() {
    GeoDecision decision = GeoDecision.denied(DenialReason.ZONE_NOT_FOUND);

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.zone()).isEmpty();
    assertThat(decision.denialReason()).contains(DenialReason.ZONE_NOT_FOUND);
  }

  @Test
  void should_reject_a_denial_when_it_has_no_reason() {
    assertThatThrownBy(() -> new GeoDecision(false, Optional.empty(), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A denied decision needs a reason and no zone");
  }

  @Test
  void should_reject_an_allowed_decision_when_it_has_a_reason() {
    assertThatThrownBy(
            () ->
                new GeoDecision(true, Optional.of(ZONE), Optional.of(DenialReason.OUTSIDE_ZONE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("An allowed decision has no reason");
  }
}
