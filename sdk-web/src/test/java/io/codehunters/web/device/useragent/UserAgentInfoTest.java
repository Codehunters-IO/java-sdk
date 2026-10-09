package io.codehunters.web.device.useragent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class UserAgentInfoTest {

  @Test
  void should_describe_an_unknown_client_with_other_families_and_no_versions() {
    assertThat(UserAgentInfo.UNKNOWN)
        .isEqualTo(new UserAgentInfo("Other", "", "Other", "", "Other"));
  }

  @Test
  void should_reject_null_components() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new UserAgentInfo("Chrome", null, "Windows", "10", "Other"))
        .withMessage("user agent components must not be null");
  }

  @Test
  void should_tell_unknown_apart_from_a_parsed_client() {
    assertThat(UserAgentInfo.UNKNOWN.isUnknown()).isTrue();
    assertThat(new UserAgentInfo("Chrome", "120", "Other", "", "Other").isUnknown()).isFalse();
  }
}
