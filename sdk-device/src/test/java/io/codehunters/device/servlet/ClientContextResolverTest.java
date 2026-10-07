package io.codehunters.device.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.codehunters.device.ip.ClientIpResolver;
import io.codehunters.device.ip.TrustedProxies;
import io.codehunters.device.useragent.UserAgentInfo;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ClientContextResolverTest {

  private static final UserAgentInfo CHROME =
      new UserAgentInfo("Chrome", "120", "Windows", "10", "Other");

  private final ClientContextResolver resolver =
      new ClientContextResolver(
          new ClientIpResolver(TrustedProxies.of(List.of("10.0.0.0/8"))), header -> CHROME, 16);

  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void should_resolve_ip_behind_trusted_proxy_and_parse_the_user_agent() {
    var request = request("10.0.0.2");
    request.addHeader("X-Forwarded-For", "198.51.100.4");
    request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0) Chrome/120");

    var context = resolver.resolve(request);

    assertThat(context.ipAddress()).contains("198.51.100.4");
    assertThat(context.userAgent()).isEqualTo(CHROME);
    assertThat(context.rawUserAgent()).isEqualTo("Mozilla/5.0 (Win");
  }

  @Test
  void should_read_every_line_of_a_repeated_forwarding_header() {
    var request = request("10.0.0.2");
    request.addHeader("Forwarded", "for=6.6.6.6");
    request.addHeader("Forwarded", "for=198.51.100.4");

    assertThat(resolver.resolve(request).ipAddress()).contains("198.51.100.4");
  }

  @Test
  void should_not_parse_when_the_request_has_no_user_agent() {
    var context = resolver.resolve(request("10.0.0.2"));

    assertThat(context.userAgent()).isEqualTo(UserAgentInfo.UNKNOWN);
    assertThat(context.rawUserAgent()).isEmpty();
  }

  @Test
  void should_resolve_a_request_once_and_reuse_the_result() {
    var request = request("10.0.0.2");

    assertThat(resolver.resolve(request)).isSameAs(resolver.resolve(request));
  }

  @Test
  void should_resolve_the_current_request_when_one_is_bound_to_the_thread() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(request("198.51.100.4")));

    assertThat(resolver.current()).flatMap(context -> context.ipAddress()).contains("198.51.100.4");
  }

  @Test
  void should_resolve_nothing_when_no_request_is_bound_to_the_thread() {
    assertThat(resolver.current()).isEmpty();
  }

  @Test
  void should_reject_a_max_length_below_one() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ClientContextResolver(
                    new ClientIpResolver(TrustedProxies.of(List.of())), header -> CHROME, 0))
        .withMessage("max user agent length must be at least 1");
  }

  @Test
  void should_reject_null_collaborators() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ClientContextResolver(null, header -> CHROME, 16))
        .withMessage("ip resolver and user agent parser must not be null");
  }

  private static MockHttpServletRequest request(String remoteAddress) {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr(remoteAddress);
    return request;
  }
}
