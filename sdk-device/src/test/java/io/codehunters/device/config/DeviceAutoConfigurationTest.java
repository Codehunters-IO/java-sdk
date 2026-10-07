package io.codehunters.device.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.device.ip.ClientIpResolver;
import io.codehunters.device.ip.ProxyChain;
import io.codehunters.device.servlet.ClientContextResolver;
import io.codehunters.device.useragent.CachingUserAgentParser;
import io.codehunters.device.useragent.UnknownUserAgentParser;
import io.codehunters.device.useragent.UserAgentInfo;
import io.codehunters.device.useragent.UserAgentParser;
import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class DeviceAutoConfigurationTest {

  private final WebApplicationContextRunner web =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(DeviceAutoConfiguration.class));

  private final ApplicationContextRunner plain =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(DeviceAutoConfiguration.class));

  @Configuration(proxyBeanMethods = false)
  static class OwnParser {
    @Bean
    UserAgentParser ownParser() {
      return header -> UserAgentInfo.UNKNOWN;
    }
  }

  @Test
  void should_register_resolvers_and_a_cached_uap_parser_in_a_servlet_application() {
    web.run(
        context -> {
          assertThat(context).hasSingleBean(ClientIpResolver.class);
          assertThat(context).hasSingleBean(ClientContextResolver.class);
          assertThat(context)
              .getBean(UserAgentParser.class)
              .isInstanceOf(CachingUserAgentParser.class);
        });
  }

  @Test
  void should_parse_with_uap_when_it_is_on_the_classpath() {
    web.run(
        context ->
            assertThat(context.getBean(UserAgentParser.class).parse("Mozilla/5.0 Chrome/120.0.1.2"))
                .extracting(UserAgentInfo::browserFamily)
                .isEqualTo("Chrome"));
  }

  @Test
  void should_fall_back_to_an_unknown_parser_when_uap_is_absent() {
    web.withClassLoader(new FilteredClassLoader("ua_parser"))
        .run(
            context ->
                assertThat(context)
                    .getBean(UserAgentParser.class)
                    .isInstanceOf(UnknownUserAgentParser.class));
  }

  @Test
  void should_skip_the_request_resolver_outside_servlet_applications() {
    plain.run(
        context -> {
          assertThat(context).hasSingleBean(ClientIpResolver.class);
          assertThat(context).doesNotHaveBean(ClientContextResolver.class);
        });
  }

  @Test
  void should_believe_forwarding_headers_from_the_configured_trusted_proxies() {
    web.withPropertyValues("codehunters.device.trusted-proxies=10.0.0.0/8")
        .run(
            context ->
                assertThat(
                        context
                            .getBean(ClientIpResolver.class)
                            .resolve(
                                new ProxyChain("10.0.0.2", List.of(), List.of("198.51.100.4"))))
                    .map(InetAddress::getHostAddress)
                    .contains("198.51.100.4"));
  }

  @Test
  void should_keep_the_application_parser_when_it_defines_one() {
    web.withUserConfiguration(OwnParser.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(UserAgentParser.class);
              assertThat(context).hasBean("ownParser");
            });
  }

  @Test
  void should_register_nothing_when_disabled() {
    web.withPropertyValues("codehunters.device.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(ClientIpResolver.class));
  }

  @Test
  void should_fail_startup_when_a_trusted_proxy_is_not_an_address_or_block() {
    web.withPropertyValues("codehunters.device.trusted-proxies=10.0.0.0/8,proxy.internal")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(
                        "codehunters.device.trusted-proxies: trusted proxy entry 1 is not an IP"
                            + " address or CIDR block"));
  }

  @Test
  void should_fail_startup_when_the_user_agent_length_is_out_of_range() {
    web.withPropertyValues("codehunters.device.user-agent.max-length=8")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(
                        "codehunters.device.user-agent.max-length must be between 16 and 8192"));
  }

  @Test
  void should_fail_startup_when_the_cache_size_is_out_of_range() {
    web.withPropertyValues("codehunters.device.user-agent.cache-size=-1")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(
                        "codehunters.device.user-agent.cache-size must be between 0 and 100000"));
  }
}
