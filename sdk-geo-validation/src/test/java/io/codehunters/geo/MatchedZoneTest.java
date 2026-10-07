package io.codehunters.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MatchedZoneTest {

  @Test
  void should_keep_every_attribute_when_all_are_given() {
    MatchedZone zone = MatchedZone.of("05001", "CITY", "Medellin", "Antioquia");

    assertThat(zone.id()).isEqualTo("05001");
    assertThat(zone.type()).contains("CITY");
    assertThat(zone.name()).contains("Medellin");
    assertThat(zone.parent()).contains("Antioquia");
  }

  @Test
  void should_leave_optional_attributes_empty_when_they_are_null_or_blank() {
    MatchedZone zone = MatchedZone.of("05001", null, " ", null);

    assertThat(zone.type()).isEmpty();
    assertThat(zone.name()).isEmpty();
    assertThat(zone.parent()).isEmpty();
  }

  @Test
  void should_keep_an_unrecognised_type_as_text_when_the_source_invents_one() {
    assertThat(MatchedZone.of("x", "HAMLET", null, null).type()).contains("HAMLET");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void should_reject_the_zone_when_its_id_is_blank(String id) {
    assertThatThrownBy(() -> MatchedZone.of(id, "CITY", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Zone id must not be blank");
  }
}
