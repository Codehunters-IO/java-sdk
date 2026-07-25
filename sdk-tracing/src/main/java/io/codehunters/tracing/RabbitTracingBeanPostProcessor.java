package io.codehunters.tracing;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Wires trace propagation into RabbitMQ infrastructure: attaches the outbound post-processor to
 * {@link RabbitTemplate}s and the inbound interceptor to listener container factories.
 */
public class RabbitTracingBeanPostProcessor implements BeanPostProcessor {

  private final TracePropagationMessagePostProcessor outboundPostProcessor;
  private final InboundTraceMethodInterceptor inboundInterceptor;

  /**
   * Creates the bean post-processor.
   *
   * @param outboundPostProcessor the processor stamping trace headers on outgoing messages
   * @param inboundInterceptor the interceptor reading trace headers on incoming messages
   */
  public RabbitTracingBeanPostProcessor(
      TracePropagationMessagePostProcessor outboundPostProcessor,
      InboundTraceMethodInterceptor inboundInterceptor) {
    this.outboundPostProcessor = outboundPostProcessor;
    this.inboundInterceptor = inboundInterceptor;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof RabbitTemplate template) {
      template.addBeforePublishPostProcessors(outboundPostProcessor);
    }
    if (bean instanceof AbstractRabbitListenerContainerFactory<?> factory) {
      Advice[] existing = factory.getAdviceChain();
      Advice[] merged;
      if (existing == null || existing.length == 0) {
        merged = new Advice[] {inboundInterceptor};
      } else {
        merged = new Advice[existing.length + 1];
        System.arraycopy(existing, 0, merged, 0, existing.length);
        merged[existing.length] = inboundInterceptor;
      }
      factory.setAdviceChain(merged);
    }
    return bean;
  }
}
