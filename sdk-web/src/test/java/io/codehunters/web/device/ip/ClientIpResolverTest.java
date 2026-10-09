package io.codehunters.web.device.ip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClientIpResolverTest {

  private static final TrustedProxies PRIVATE_PROXIES =
      TrustedProxies.of(List.of("10.0.0.0/8", "2001:db8::/32"));
  private static final ClientIpResolver BEHIND_PRIVATE_PROXIES =
      new ClientIpResolver(PRIVATE_PROXIES, ForwardingHeader.X_FORWARDED_FOR);
  private static final ClientIpResolver BEHIND_RFC_7239_PROXIES =
      new ClientIpResolver(PRIVATE_PROXIES, ForwardingHeader.FORWARDED);

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          203.0.113.9  | 1.1.1.1                     | 203.0.113.9
          10.0.0.2     | 198.51.100.4                | 198.51.100.4
          10.0.0.2     | 6.6.6.6, 198.51.100.4       | 198.51.100.4
          10.0.0.2     | 198.51.100.4, 10.0.0.3      | 198.51.100.4
          10.0.0.2     | 10.0.0.5,10.0.0.3           | 10.0.0.5
          10.0.0.2     | 6.6.6.6, garbage            | 10.0.0.2
          10.0.0.2     | 6.6.6.6, unknown, 10.0.0.3  | 10.0.0.3
          10.0.0.2     | 198.51.100.4:5555           | 198.51.100.4
          10.0.0.2     | '   '                       | 10.0.0.2
          10.0.0.2     | 6.6.6.6,, 198.51.100.4      | 198.51.100.4
          2001:db8::1  | 198.51.100.4                | 198.51.100.4
          2001:db8::1  | 2001:db8::9, 2001:db8::8    | 2001:db8:0:0:0:0:0:9
          10.0.0.2     | ", 198.51.100.4             | 198.51.100.4
          10.0.0.2     | "a, b", 198.51.100.4        | 198.51.100.4
          10.0.0.2     | fe80::1%en0                 | 10.0.0.2
          """)
  void should_resolve_the_client_from_x_forwarded_for(
      String remote, String forwardedFor, String expected) {
    var chain = new ProxyChain(remote, List.of(), List.of(forwardedFor));

    assertThat(BEHIND_PRIVATE_PROXIES.resolve(chain))
        .map(InetAddress::getHostAddress)
        .contains(expected);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          10.0.0.2  | for=198.51.100.4                              | 198.51.100.4
          10.0.0.2  | for="[2001:db9::7]:4711"                      | 2001:db9:0:0:0:0:0:7
          10.0.0.2  | for=6.6.6.6, for=198.51.100.4;proto=https     | 198.51.100.4
          10.0.0.2  | For=198.51.100.4;By=10.0.0.2                  | 198.51.100.4
          10.0.0.2  | for="198.51.100.4:8080"                       | 198.51.100.4
          10.0.0.2  | for=_hidden                                   | 10.0.0.2
          10.0.0.2  | for=unknown                                   | 10.0.0.2
          10.0.0.2  | proto=https                                   | 10.0.0.2
          10.0.0.2  | for="_a,b", for=198.51.100.4                  | 198.51.100.4
          10.0.0.2  | for="_a;b=c";proto=http, for=198.51.100.4     | 198.51.100.4
          10.0.0.2  | for="\\"x", for=198.51.100.4                  | 198.51.100.4
          10.0.0.2  | for=198.51.100.4, for=10.0.0.9                | 198.51.100.4
          10.0.0.2  | for="unterminated                             | 10.0.0.2
          10.0.0.2  | for="x, for=198.51.100.4                      | 198.51.100.4
          10.0.0.2  | for="x;y, for=198.51.100.4;proto=https        | 198.51.100.4
          203.0.113.9 | for=198.51.100.4                            | 203.0.113.9
          """)
  void should_resolve_the_client_from_forwarded(String remote, String forwarded, String expected) {
    var chain = new ProxyChain(remote, List.of(forwarded), List.of());

    assertThat(BEHIND_RFC_7239_PROXIES.resolve(chain))
        .map(InetAddress::getHostAddress)
        .contains(expected);
  }

  @Test
  void should_ignore_a_forwarded_header_sent_by_the_client_when_proxies_write_x_forwarded_for() {
    var chain = new ProxyChain("10.0.0.2", List.of("for=6.6.6.6"), List.of("198.51.100.4"));

    assertThat(BEHIND_PRIVATE_PROXIES.resolve(chain))
        .map(InetAddress::getHostAddress)
        .contains("198.51.100.4");
  }

  @Test
  void should_ignore_x_forwarded_for_sent_by_the_client_when_proxies_write_forwarded() {
    var chain = new ProxyChain("10.0.0.2", List.of("for=198.51.100.4"), List.of("6.6.6.6"));

    assertThat(BEHIND_RFC_7239_PROXIES.resolve(chain))
        .map(InetAddress::getHostAddress)
        .contains("198.51.100.4");
  }

  @Test
  void should_read_header_lines_in_order_when_a_header_repeats() {
    var chain = new ProxyChain("10.0.0.2", List.of(), List.of("6.6.6.6", "198.51.100.4"));

    assertThat(BEHIND_PRIVATE_PROXIES.resolve(chain))
        .map(InetAddress::getHostAddress)
        .contains("198.51.100.4");
  }

  @Test
  void should_use_the_remote_address_only_when_no_proxy_is_trusted() {
    var resolver =
        new ClientIpResolver(TrustedProxies.of(List.of()), ForwardingHeader.X_FORWARDED_FOR);
    var chain = new ProxyChain("10.0.0.2", List.of("for=1.1.1.1"), List.of("1.1.1.1"));

    assertThat(resolver.resolve(chain)).map(InetAddress::getHostAddress).contains("10.0.0.2");
  }

  @Test
  void should_drop_the_zone_id_of_a_link_local_remote_address() {
    var resolver =
        new ClientIpResolver(TrustedProxies.of(List.of()), ForwardingHeader.X_FORWARDED_FOR);
    var chain = new ProxyChain("fe80:0:0:0:0:0:0:1%en0", List.of(), List.of());

    assertThat(resolver.resolve(chain))
        .map(InetAddress::getHostAddress)
        .contains("fe80:0:0:0:0:0:0:1");
  }

  @Test
  void should_resolve_nothing_when_the_remote_address_is_not_an_ip_literal() {
    var chain = new ProxyChain("proxy.internal", List.of(), List.of("198.51.100.4"));

    assertThat(BEHIND_PRIVATE_PROXIES.resolve(chain)).isEmpty();
  }

  @Test
  void should_resolve_nothing_when_the_remote_address_is_missing() {
    var chain = new ProxyChain(null, List.of(), List.of("198.51.100.4"));

    assertThat(BEHIND_PRIVATE_PROXIES.resolve(chain)).isEmpty();
  }

  @Test
  void should_reject_null_settings() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ClientIpResolver(null, ForwardingHeader.FORWARDED))
        .withMessage("trusted proxies and forwarding header must not be null");
  }

  @Test
  void should_reject_a_null_chain() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> BEHIND_PRIVATE_PROXIES.resolve(null))
        .withMessage("proxy chain must not be null");
  }
}
