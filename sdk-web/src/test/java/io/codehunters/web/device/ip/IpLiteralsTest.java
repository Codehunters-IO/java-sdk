package io.codehunters.web.device.ip;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IpLiteralsTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          203.0.113.7               | 203.0.113.7
          '  203.0.113.7 '          | 203.0.113.7
          203.0.113.7:8080          | 203.0.113.7
          0.0.0.0                   | 0.0.0.0
          [2001:db8::1]             | 2001:db8:0:0:0:0:0:1
          [2001:db8::1]:443         | 2001:db8:0:0:0:0:0:1
          2001:db8::1               | 2001:db8:0:0:0:0:0:1
          2001:DB8::A               | 2001:db8:0:0:0:0:0:a
          ::1                       | 0:0:0:0:0:0:0:1
          ::ffff:203.0.113.7        | 203.0.113.7
          """)
  void should_parse_ip_literals_when_well_formed(String text, String expected) {
    assertThat(IpLiterals.parse(text)).map(InetAddress::getHostAddress).contains(expected);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "   ",
        "unknown",
        "_hidden",
        "example.com",
        "localhost",
        "256.1.1.1",
        "01.2.3.4",
        "1.2.3",
        "1.2.3.4.5",
        "1..3.4",
        "1.2.3.4:",
        "1.2.3.4:abc",
        "1.2.3.4:99999999999",
        "2001:db8::1%eth0",
        "[2001:db8::1",
        "[::1]x",
        "[::1]:",
        "[1.2.3.4]",
        "fe80::zz",
        "1:2:3:4:5:6:7:8:9",
        "+1.2.3.4",
        "..::",
        ".::1",
        ".1::",
        "١.٢.٣.٤"
      })
  void should_reject_text_when_it_is_not_an_ip_literal(String text) {
    assertThat(IpLiterals.parse(text)).isEmpty();
  }
}
