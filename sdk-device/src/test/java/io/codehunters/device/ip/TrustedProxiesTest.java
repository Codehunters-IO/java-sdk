package io.codehunters.device.ip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TrustedProxiesTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          10.0.0.0/8       | 10.1.2.3          | true
          10.0.0.0/8       | 11.0.0.1          | false
          192.168.1.0/25   | 192.168.1.127     | true
          192.168.1.0/25   | 192.168.1.200     | false
          203.0.113.7      | 203.0.113.7       | true
          203.0.113.7      | 203.0.113.8       | false
          0.0.0.0/0        | 198.51.100.1      | true
          10.0.0.0/8       | ::1               | false
          2001:db8::/32    | 2001:db8:1::1     | true
          2001:db8::/32    | 2001:db9::1       | false
          ::/0             | 2001:db8::1       | true
          ::/0             | 10.0.0.1          | false
          ::1              | ::1               | true
          10.0.0.1/8       | 10.200.0.1        | true
          """)
  void should_tell_whether_an_address_is_trusted(String block, String address, boolean trusted) {
    var proxies = TrustedProxies.of(List.of(block));

    assertThat(proxies.contains(IpLiterals.parse(address).orElseThrow())).isEqualTo(trusted);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "10.0.0.0/33",
        "::/129",
        "example.com/8",
        "example.com",
        "10.0.0.0/-1",
        "10.0.0.0/",
        "/8",
        "10.0.0.0/8/1",
        "10.0.0.0/x",
        ""
      })
  void should_reject_an_entry_when_it_is_not_an_address_or_block(String entry) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> TrustedProxies.of(List.of("10.0.0.0/8", entry)))
        .withMessage("trusted proxy entry 1 is not an IP address or CIDR block");
  }

  @Test
  void should_trust_nothing_when_no_entries_are_given() {
    var proxies = TrustedProxies.of(List.of());

    assertThat(proxies.isEmpty()).isTrue();
    assertThat(proxies.contains(IpLiterals.parse("10.0.0.1").orElseThrow())).isFalse();
  }

  @Test
  void should_not_be_empty_when_an_entry_is_given() {
    assertThat(TrustedProxies.of(List.of("10.0.0.0/8")).isEmpty()).isFalse();
  }

  @Test
  void should_reject_a_null_list() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> TrustedProxies.of(null))
        .withMessage("trusted proxies must not be null");
  }
}
