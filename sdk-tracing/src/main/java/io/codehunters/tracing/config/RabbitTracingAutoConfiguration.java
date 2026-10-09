package io.codehunters.tracing.config;

import io.codehunters.tracing.amqp.InboundTraceMethodInterceptor;
import io.codehunters.tracing.amqp.RabbitTracingBeanPostProcessor;
import io.codehunters.tracing.amqp.TracePropagationMessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propagates the trace context through RabbitMQ. This module is the single owner of the outbound
 * trace post-processor: it is added to each {@link RabbitTemplate} once.
 */
@AutoConfiguration
@ConditionalOnClass({RabbitTemplate.class, JsonMapper.class})
@ConditionalOnBooleanProperty(name = "codehunters.tracing.enabled", matchIfMissing = true)
public class RabbitTracingAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public RabbitTracingAutoConfiguration() {}

  /**
   * Provides the outbound message post-processor.
   *
   * @return a {@link TracePropagationMessagePostProcessor}
   */
  @Bean
  @ConditionalOnMissingBean
  public TracePropagationMessagePostProcessor tracePropagationMessagePostProcessor() {
    return new TracePropagationMessagePostProcessor();
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
   * Provides the bean post-processor that attaches tracing to RabbitMQ infrastructure. Static, and
   * resolving its collaborators lazily, so the application's beans stay eligible for proxying.
   *
   * @param outbound the outbound message post-processor
   * @param inbound the inbound listener interceptor
   * @return a {@link RabbitTracingBeanPostProcessor}
   */
  @Bean
  @ConditionalOnMissingBean
  public static RabbitTracingBeanPostProcessor rabbitTracingBeanPostProcessor(
      ObjectProvider<TracePropagationMessagePostProcessor> outbound,
      ObjectProvider<InboundTraceMethodInterceptor> inbound) {
    return new RabbitTracingBeanPostProcessor(outbound, inbound);
  }
}
