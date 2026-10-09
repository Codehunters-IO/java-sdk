package io.codehunters.web.device.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.codehunters.web.device.ClientContext;
import io.codehunters.web.device.ip.ClientIpResolver;
import io.codehunters.web.device.ip.ForwardingHeader;
import io.codehunters.web.device.ip.TrustedProxies;
import io.codehunters.web.device.useragent.UnknownUserAgentParser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ForwardedClientIpFilterTest {

  private static final String PROXY = "10.0.0.2";
  private static final String CLIENT = "198.51.100.4";

  private static final ClientIpResolver BEHIND_PROXY =
      new ClientIpResolver(
          TrustedProxies.of(List.of("10.0.0.0/8")), ForwardingHeader.X_FORWARDED_FOR);

  private static final ClientIpResolver TRUSTING_NOBODY =
      new ClientIpResolver(TrustedProxies.of(List.of()), ForwardingHeader.X_FORWARDED_FOR);

  @Test
  void should_expose_the_client_as_remote_address_when_a_trusted_proxy_forwarded_it() {
    var forwarded = filter(BEHIND_PROXY, request(PROXY, CLIENT));

    assertThat(forwarded.getRemoteAddr()).isEqualTo(CLIENT);
    assertThat(forwarded.getRemoteHost()).isEqualTo(CLIENT);
  }

  @Test
  void should_keep_the_connection_address_when_no_proxy_is_trusted() {
    var forwarded = filter(TRUSTING_NOBODY, request("203.0.113.7", "6.6.6.6"));

    assertThat(forwarded.getRemoteAddr()).isEqualTo("203.0.113.7");
  }

  @Test
  void should_hide_the_forwarding_headers_from_the_rest_of_the_chain() {
    var original = request(PROXY, CLIENT);
    original.addHeader("Forwarded", "for=6.6.6.6");
    original.addHeader("Accept", "text/plain");

    var forwarded = filter(BEHIND_PROXY, original);

    assertThat(forwarded.getHeader("x-forwarded-for")).isNull();
    assertThat(Collections.list(forwarded.getHeaders("Forwarded"))).isEmpty();
    assertThat(Collections.list(forwarded.getHeaderNames())).containsExactly("Accept");
  }

  @Test
  void should_leave_the_request_untouched_when_the_remote_address_is_not_an_ip_literal() {
    var original = request("proxy.internal", CLIENT);

    var forwarded = filter(BEHIND_PROXY, original);

    assertThat(forwarded).isSameAs(original);
  }

  @Test
  void should_let_the_client_context_see_the_same_address_as_the_rest_of_the_chain() {
    var forwarded = filter(BEHIND_PROXY, request(PROXY, CLIENT));
    var resolver = new ClientContextResolver(BEHIND_PROXY, new UnknownUserAgentParser(), 512);

    ClientContext context = resolver.resolve(forwarded);

    assertThat(context.ipAddress()).contains(forwarded.getRemoteAddr());
  }

  @Test
  void should_run_before_spring_security_and_the_framework_filters() {
    assertThat(new ForwardedClientIpFilter(BEHIND_PROXY).getOrder())
        .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }

  @Test
  void should_reject_a_null_resolver() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ForwardedClientIpFilter(null))
        .withMessage("client ip resolver must not be null");
  }

  private static MockHttpServletRequest request(String remoteAddress, String xForwardedFor) {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr(remoteAddress);
    request.addHeader("X-Forwarded-For", xForwardedFor);
    return request;
  }

  private static HttpServletRequest filter(ClientIpResolver resolver, HttpServletRequest request) {
    var chain = new MockFilterChain();
    try {
      new ForwardedClientIpFilter(resolver).doFilter(request, new MockHttpServletResponse(), chain);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return (HttpServletRequest) chain.getRequest();
  }
}
