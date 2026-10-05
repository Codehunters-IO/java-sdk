package io.codehunters.tracing;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;
import org.aopalliance.aop.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Attaches trace propagation to RabbitMQ infrastructure: the outbound post-processor to every
 * {@link RabbitTemplate} and the inbound interceptor to every listener container factory, each at
 * most once. The tracing beans are resolved only when the first RabbitMQ bean is processed, so
 * nothing is initialised early.
 */
public class RabbitTracingBeanPostProcessor implements BeanPostProcessor {

  private static final Logger log = LoggerFactory.getLogger(RabbitTracingBeanPostProcessor.class);

  private final ObjectProvider<TracePropagationMessagePostProcessor> outbound;
  private final ObjectProvider<InboundTraceMethodInterceptor> inbound;

  /**
   * Creates the bean post-processor.
   *
   * @param outbound the processor stamping trace headers on outgoing messages
   * @param inbound the interceptor reading trace headers on incoming messages
   */
  public RabbitTracingBeanPostProcessor(
      ObjectProvider<TracePropagationMessagePostProcessor> outbound,
      ObjectProvider<InboundTraceMethodInterceptor> inbound) {
    this.outbound = outbound;
    this.inbound = inbound;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof RabbitTemplate template) {
      addOutbound(template);
    }
    if (bean instanceof AbstractRabbitListenerContainerFactory<?> factory) {
      addInbound(factory);
    }
    return bean;
  }

  private void addOutbound(RabbitTemplate template) {
    Collection<?> existing = template.getBeforePublishPostProcessors();
    if (existing != null
        && existing.stream().anyMatch(TracePropagationMessagePostProcessor.class::isInstance)) {
      return;
    }
    TracePropagationMessagePostProcessor processor = outbound.getIfUnique();
    if (processor == null) {
      log.warn("No single TracePropagationMessagePostProcessor bean; RabbitTemplate not traced");
      return;
    }
    template.addBeforePublishPostProcessors(processor);
  }

  private void addInbound(AbstractRabbitListenerContainerFactory<?> factory) {
    Advice[] existing = factory.getAdviceChain() == null ? new Advice[0] : factory.getAdviceChain();
    if (Arrays.stream(existing).noneMatch(InboundTraceMethodInterceptor.class::isInstance)) {
      factory.setAdviceChain(
          Stream.concat(Arrays.stream(existing), Stream.of(inbound.getObject()))
              .toArray(Advice[]::new));
    }
  }
}
