package io.codehunters.web.device.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.web.device.cache.CachingUserAgentParser;
import io.codehunters.web.device.ip.ClientIpResolver;
import io.codehunters.web.device.ip.ProxyChain;
import io.codehunters.web.device.servlet.ClientContextResolver;
import io.codehunters.web.device.servlet.ForwardedClientIpFilter;
import io.codehunters.web.device.useragent.UnknownUserAgentParser;
import io.codehunters.web.device.useragent.UserAgentInfo;
import io.codehunters.web.device.useragent.UserAgentParser;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

class DeviceAutoConfigurationTest {

  private final WebApplicationContextRunner web =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(DeviceAutoConfiguration.class));

  private final ApplicationContextRunner plain =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(DeviceAutoConfiguration.class));

  private static final String FILTER_ENABLED =
      "codehunters.web.device.forwarded-request-filter.enabled=true";

  @Configuration(proxyBeanMethods = false)
  static class OwnParser {
    @Bean
    UserAgentParser ownParser() {
      return header -> UserAgentInfo.UNKNOWN;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnForwardedHeaderFilter {
    @Bean
    ForwardedHeaderFilter springForwardedHeaderFilter() {
      return new ForwardedHeaderFilter();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class RegisteredForwardedHeaderFilter {
    @Bean
    FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
      return new FilterRegistrationBean<>(new ForwardedHeaderFilter());
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
  void should_register_nothing_outside_servlet_applications() {
    plain.run(
        context -> {
          assertThat(context).doesNotHaveBean(ClientIpResolver.class);
          assertThat(context).doesNotHaveBean(UserAgentParser.class);
          assertThat(context).doesNotHaveBean(ClientContextResolver.class);
        });
  }

  @Test
  void should_publish_user_agent_cache_metrics_when_micrometer_is_present() {
    web.run(
        context -> {
          var registry = new SimpleMeterRegistry();
          context.getBean("userAgentCacheMetrics", MeterBinder.class).bindTo(registry);
          context.getBean(UserAgentParser.class).parse("Mozilla/5.0 Chrome/120.0.1.2");

          assertThat(
                  registry
                      .get("cache.gets")
                      .tag("cache", "codehunters.web.device.user-agent.cache")
                      .tag("result", "miss")
                      .functionCounter()
                      .count())
              .isEqualTo(1.0);
        });
  }

  @Test
  void should_skip_user_agent_cache_metrics_when_micrometer_is_absent() {
    web.withClassLoader(new FilteredClassLoader("io.micrometer"))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean("userAgentCacheMetrics");
            });
  }

  @Test
  void should_not_rewrite_the_remote_address_by_default() {
    web.run(context -> assertThat(context).doesNotHaveBean(ForwardedClientIpFilter.class));
  }

  @Test
  void should_register_the_forwarded_request_filter_when_enabled() {
    web.withPropertyValues("codehunters.web.device.forwarded-request-filter.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(ForwardedClientIpFilter.class));
  }

  @Test
  void
      should_fail_startup_when_the_filter_is_combined_with_the_framework_forward_headers_strategy() {
    web.withPropertyValues(FILTER_ENABLED, "server.forward-headers-strategy=framework")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageStartingWith(
                        "codehunters.web.device.forwarded-request-filter.enabled=true cannot be"
                            + " combined with server.forward-headers-strategy=framework"));
  }

  @Test
  void should_fail_startup_when_the_filter_is_combined_with_a_forwarded_header_filter_bean() {
    web.withPropertyValues(FILTER_ENABLED)
        .withUserConfiguration(OwnForwardedHeaderFilter.class)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageStartingWith(
                        "codehunters.web.device.forwarded-request-filter.enabled=true cannot be"
                            + " combined with Spring's ForwardedHeaderFilter (bean"
                            + " 'springForwardedHeaderFilter')"));
  }

  @Test
  void should_fail_startup_when_the_filter_is_combined_with_a_registered_forwarded_header_filter() {
    web.withPropertyValues(FILTER_ENABLED)
        .withUserConfiguration(RegisteredForwardedHeaderFilter.class)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageStartingWith(
                        "codehunters.web.device.forwarded-request-filter.enabled=true cannot be"
                            + " combined with Spring's ForwardedHeaderFilter (bean"
                            + " 'forwardedHeaderFilter')"));
  }

  @Test
  void should_start_when_the_filter_is_combined_with_the_native_forward_headers_strategy() {
    web.withPropertyValues(FILTER_ENABLED, "server.forward-headers-strategy=native")
        .run(context -> assertThat(context).hasSingleBean(ForwardedClientIpFilter.class));
  }

  @Test
  void should_believe_forwarding_headers_from_the_configured_trusted_proxies() {
    web.withPropertyValues("codehunters.web.device.trusted-proxies=10.0.0.0/8")
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
  void should_read_forwarded_instead_when_configured() {
    web.withPropertyValues(
            "codehunters.web.device.trusted-proxies=10.0.0.0/8",
            "codehunters.web.device.forwarding-header=forwarded")
        .run(
            context ->
                assertThat(
                        context
                            .getBean(ClientIpResolver.class)
                            .resolve(
                                new ProxyChain(
                                    "10.0.0.2", List.of("for=198.51.100.4"), List.of("6.6.6.6"))))
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
    web.withPropertyValues("codehunters.web.device.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(ClientIpResolver.class));
  }

  @Test
  void should_fail_startup_when_a_trusted_proxy_is_not_an_address_or_block() {
    web.withPropertyValues("codehunters.web.device.trusted-proxies=10.0.0.0/8,proxy.internal")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(
                        "codehunters.web.device.trusted-proxies: trusted proxy entry 1 is not an IP"
                            + " address or CIDR block"));
  }

  @Test
  void should_fail_startup_when_the_user_agent_length_is_out_of_range() {
    web.withPropertyValues("codehunters.web.device.user-agent.max-length=8")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(
                        "codehunters.web.device.user-agent.max-length must be between 16 and 8192"));
  }

  @Test
  void should_fail_startup_when_the_cached_headers_could_exceed_64_mb() {
    web.withPropertyValues(
            "codehunters.web.device.user-agent.max-length=8192",
            "codehunters.web.device.user-agent.cache-size=4097")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(
                        "codehunters.web.device.user-agent.max-length x cache-size must not exceed"
                            + " 33554432 characters (64 MB of cached headers)"));
  }

  @Test
  void should_accept_the_largest_cache_that_fits_in_64_mb() {
    web.withPropertyValues(
            "codehunters.web.device.user-agent.max-length=8192",
            "codehunters.web.device.user-agent.cache-size=4096")
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void should_start_without_servlet_or_spring_web_on_the_classpath() {
    plain
        .withClassLoader(new FilteredClassLoader("jakarta.servlet", "org.springframework.web"))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ClientIpResolver.class);
            });
  }

  @Test
  void should_fail_startup_when_the_cache_size_is_out_of_range() {
    web.withPropertyValues("codehunters.web.device.user-agent.cache-size=-1")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(
                        "codehunters.web.device.user-agent.cache-size must be between 0 and 100000"));
  }
}
