package io.codehunters.device.uap;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.device.useragent.UserAgentInfo;
import org.junit.jupiter.api.Test;

class UapUserAgentParserTest {

  private static final UapUserAgentParser PARSER = new UapUserAgentParser();

  private static final String CHROME_ON_WINDOWS =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
          + " Chrome/120.0.6099.71 Safari/537.36";
  private static final String SAFARI_ON_IPHONE =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15"
          + " (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1";

  @Test
  void should_read_browser_and_os_of_a_desktop_browser() {
    assertThat(PARSER.parse(CHROME_ON_WINDOWS))
        .isEqualTo(new UserAgentInfo("Chrome", "120.0.6099", "Windows", "10", "Other"));
  }

  @Test
  void should_read_the_device_of_a_phone() {
    assertThat(PARSER.parse(SAFARI_ON_IPHONE))
        .isEqualTo(new UserAgentInfo("Mobile Safari", "17.1", "iOS", "17.1", "iPhone"));
  }

  @Test
  void should_describe_an_unrecognised_header_as_unknown() {
    assertThat(PARSER.parse("curl-like-thing")).isEqualTo(UserAgentInfo.UNKNOWN);
  }
}
