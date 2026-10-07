package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.device.config.DeviceAutoConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuditDeviceContextTest {

  private final WebApplicationContextRunner web =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(AuditAutoConfiguration.class, DeviceAutoConfiguration.class))
          .withPropertyValues("codehunters.device.trusted-proxies=10.0.0.0/8");

  @Configuration(proxyBeanMethods = false)
  static class OwnProvider {
    @Bean
    AuditContextProvider ownProvider() {
      return new AuditContextProvider() {
        @Override
        public Optional<String> getActorId() {
          return Optional.of("own");
        }

        @Override
        public Optional<String> getActorType() {
          return Optional.empty();
        }

        @Override
        public Optional<String> getIpAddress() {
          return Optional.empty();
        }

        @Override
        public Optional<String> getSourceService() {
          return Optional.empty();
        }
      };
    }
  }

  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void should_read_ip_and_user_agent_of_the_current_request_when_the_device_sdk_is_present() {
    bindRequest();

    web.run(
        context -> {
          var provider = context.getBean(AuditContextProvider.class);
          assertThat(provider.getIpAddress()).contains("198.51.100.4");
          assertThat(provider.getUserAgent()).contains("Mozilla/5.0 Chrome/120.0");
        });
  }

  @Test
  void should_report_no_ip_or_user_agent_outside_a_request() {
    web.run(
        context -> {
          var provider = context.getBean(AuditContextProvider.class);
          assertThat(provider.getIpAddress()).isEmpty();
          assertThat(provider.getUserAgent()).isEmpty();
        });
  }

  @Test
  void should_report_no_user_agent_when_the_request_has_none() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));

    web.run(
        context ->
            assertThat(context.getBean(AuditContextProvider.class).getUserAgent()).isEmpty());
  }

  @Test
  void should_leave_actor_and_source_service_to_the_application() {
    bindRequest();

    web.run(
        context -> {
          var provider = context.getBean(AuditContextProvider.class);
          assertThat(provider.getActorId()).isEmpty();
          assertThat(provider.getActorType()).isEmpty();
          assertThat(provider.getSourceService()).isEmpty();
        });
  }

  @Test
  void should_keep_the_application_provider_when_it_defines_one() {
    web.withUserConfiguration(OwnProvider.class)
        .run(
            context ->
                assertThat(context.getBean(AuditContextProvider.class).getActorId())
                    .contains("own"));
  }

  @Test
  void should_use_the_no_op_provider_when_the_device_sdk_is_absent() {
    new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AuditAutoConfiguration.class))
        .withClassLoader(new FilteredClassLoader("io.codehunters.device"))
        .run(
            context ->
                assertThat(context)
                    .getBean(AuditContextProvider.class)
                    .isInstanceOf(NoOpAuditContextProvider.class));
  }

  @Test
  void should_use_the_no_op_provider_outside_servlet_applications() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(AuditAutoConfiguration.class, DeviceAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .getBean(AuditContextProvider.class)
                    .isInstanceOf(NoOpAuditContextProvider.class));
  }

  @Test
  void should_use_the_no_op_provider_without_servlet_or_spring_web_on_the_classpath() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(AuditAutoConfiguration.class, DeviceAutoConfiguration.class))
        .withClassLoader(new FilteredClassLoader("jakarta.servlet", "org.springframework.web"))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .getBean(AuditContextProvider.class)
                  .isInstanceOf(NoOpAuditContextProvider.class);
            });
  }

  private static void bindRequest() {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.2");
    request.addHeader("X-Forwarded-For", "198.51.100.4");
    request.addHeader("User-Agent", "Mozilla/5.0 Chrome/120.0");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }
}
