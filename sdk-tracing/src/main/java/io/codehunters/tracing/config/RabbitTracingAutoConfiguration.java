package io.codehunters.tracing.config;

import io.codehunters.tracing.InboundTraceMethodInterceptor;
import io.codehunters.tracing.RabbitTracingBeanPostProcessor;
import io.codehunters.tracing.TracePropagationMessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/** RabbitMQ tracing auto-configuration: wires inbound and outbound trace propagation. */
@AutoConfiguration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitTracingAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public RabbitTracingAutoConfiguration() {}

  /**
   * Provides the outbound message post-processor.
   *
   * @param jsonMapper the mapper used to read trace IDs from message bodies
   * @return a {@link TracePropagationMessagePostProcessor}
   */
  @Bean(name = "tracePropagationMessagePostProcessor")
  @ConditionalOnMissingBean(name = "tracePropagationMessagePostProcessor")
  public TracePropagationMessagePostProcessor tracePropagationMessagePostProcessor(
      JsonMapper jsonMapper) {
    return new TracePropagationMessagePostProcessor(jsonMapper);
  }

  /**
   * Provides the inbound listener interceptor.
   *
   * @return an {@link InboundTraceMethodInterceptor}
   */
  @Bean
  @ConditionalOnMissingBean
  public InboundTraceMethodInterceptor inboundTraceMethodInterceptor() {
    return new InboundTraceMethodInterceptor();
  }

  /**
   * Provides the bean post-processor that attaches tracing to RabbitMQ infrastructure.
   *
   * @param outbound the outbound message post-processor
   * @param inbound the inbound listener interceptor
   * @return a {@link RabbitTracingBeanPostProcessor}
   */
  @Bean
  @ConditionalOnMissingBean
  public static RabbitTracingBeanPostProcessor rabbitTracingBeanPostProcessor(
      TracePropagationMessagePostProcessor outbound, InboundTraceMethodInterceptor inbound) {
    return new RabbitTracingBeanPostProcessor(outbound, inbound);
  }
}
