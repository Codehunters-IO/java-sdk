package io.codehunters.audit;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class TestConfig {

  @Bean
  AuditEventPublisherPort auditEventPublisher() {
    return new InMemoryAuditEventPublisher();
  }

  @Bean
  AuditContextProvider auditContextProvider() {
    return new AuditContextProvider() {
      @Override
      public Optional<String> getActorId() {
        return Optional.of("user-123");
      }

      @Override
      public Optional<String> getActorType() {
        return Optional.of("USER");
      }

      @Override
      public Optional<String> getIpAddress() {
        return Optional.of("192.168.1.1");
      }

      @Override
      public Optional<String> getSourceService() {
        return Optional.of("test-service");
      }

      @Override
      public Optional<String> getUserAgent() {
        return Optional.of("TestAgent/1.0");
      }

      @Override
      public Optional<String> getHttpMethod() {
        return Optional.of("POST");
      }

      @Override
      public Optional<String> getResource() {
        return Optional.of("/api/orders");
      }
    };
  }

  @Bean
  AuditAspect auditAspect(AuditEventPublisherPort publisher, AuditContextProvider contextProvider) {
    return new AuditAspect(publisher, contextProvider);
  }

  @Bean
  OrderService orderService() {
    return new OrderService();
  }
}
