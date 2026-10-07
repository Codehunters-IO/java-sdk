package io.codehunters.device.useragent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnknownUserAgentParserTest {

  @Test
  void should_describe_every_client_as_unknown() {
    assertThat(new UnknownUserAgentParser().parse("Mozilla/5.0 Chrome/120.0"))
        .isEqualTo(UserAgentInfo.UNKNOWN);
  }
}
