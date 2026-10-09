package io.codehunters.audit.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.audit.internal.aspect.AuditAspect;
import io.codehunters.audit.internal.aspect.InMemoryAuditEventPublisher;
import io.codehunters.audit.internal.aspect.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ExtendWith(OutputCaptureExtension.class)
class AuditAutoConfigurationProxyTest {

  private static final String PROXY_WARNING = "no AspectJ auto-proxy creator";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(AopAutoConfiguration.class, AuditAutoConfiguration.class))
          .withUserConfiguration(AuditedApplication.class);

  @Test
  void should_audit_through_boot_aop_auto_configuration() {
    runner.run(
        context -> {
          context.getBean(OrderService.class).findOrder("ORD-7");

          assertThat(context.getBean(InMemoryAuditEventPublisher.class).getLastEvent().entityId())
              .isEqualTo("ORD-7");
        });
  }

  @Test
  void should_not_force_proxying_when_spring_aop_auto_is_false() {
    runner
        .withPropertyValues("spring.aop.auto=false")
        .run(
            context ->
                assertThat(AopUtils.isAopProxy(context.getBean(OrderService.class))).isFalse());
  }

  @Test
  void should_warn_when_no_aspectj_proxy_creator_applies_the_aspect(CapturedOutput output) {
    runner
        .withPropertyValues("spring.aop.auto=false")
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).contains(PROXY_WARNING);
  }

  @Test
  void should_not_warn_when_boot_aop_applies_the_aspect(CapturedOutput output) {
    runner.run(context -> assertThat(context).hasNotFailed());

    assertThat(output).doesNotContain(PROXY_WARNING);
  }

  @Test
  void should_fail_startup_without_proxy_creator_when_fail_on_error_is_set() {
    runner
        .withPropertyValues("spring.aop.auto=false", "codehunters.audit.fail-on-error=true")
        .run(context -> assertThat(context).getFailure().hasMessageContaining(PROXY_WARNING));
  }

  @Test
  void should_not_register_the_aspect_when_aspectj_weaver_is_absent() {
    runner
        .withClassLoader(new FilteredClassLoader("org.aspectj.weaver"))
        .run(context -> assertThat(context).doesNotHaveBean(AuditAspect.class));
  }

  @Configuration(proxyBeanMethods = false)
  static class AuditedApplication {

    @Bean
    InMemoryAuditEventPublisher auditEventPublisher() {
      return new InMemoryAuditEventPublisher();
    }

    @Bean
    OrderService orderService() {
      return new OrderService();
    }
  }
}
