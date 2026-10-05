package io.codehunters.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class RabbitTracingBeanPostProcessorTest {

  private final TracePropagationMessagePostProcessor outbound =
      new TracePropagationMessagePostProcessor();
  private final InboundTraceMethodInterceptor inbound = new InboundTraceMethodInterceptor();
  private final RabbitTracingBeanPostProcessor processor = processorFor(outbound, inbound);

  @Test
  void should_add_outbound_post_processor_to_rabbit_template() {
    RabbitTemplate template = new RabbitTemplate(mock(ConnectionFactory.class));

    processor.postProcessAfterInitialization(template, "rabbitTemplate");

    assertThat(template.getBeforePublishPostProcessors()).containsExactly(outbound);
  }

  @Test
  void should_not_add_outbound_post_processor_twice() {
    RabbitTemplate template = new RabbitTemplate(mock(ConnectionFactory.class));
    template.addBeforePublishPostProcessors(outbound);

    processor.postProcessAfterInitialization(template, "rabbitTemplate");

    assertThat(template.getBeforePublishPostProcessors()).containsExactly(outbound);
  }

  @Test
  void should_append_inbound_interceptor_to_listener_factory_advice() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    Advice existing = mock(Advice.class);
    factory.setAdviceChain(existing);

    processor.postProcessAfterInitialization(factory, "factory");

    assertThat(factory.getAdviceChain()).containsExactly(existing, inbound);
  }

  @Test
  void should_not_resolve_tracing_beans_until_a_rabbit_bean_is_processed() {
    var beans = new StaticListableBeanFactory();
    var lazy =
        new RabbitTracingBeanPostProcessor(
            beans.getBeanProvider(TracePropagationMessagePostProcessor.class),
            beans.getBeanProvider(InboundTraceMethodInterceptor.class));

    Object bean = new Object();

    assertThat(lazy.postProcessAfterInitialization(bean, "other")).isSameAs(bean);
  }

  private static RabbitTracingBeanPostProcessor processorFor(
      TracePropagationMessagePostProcessor outbound, InboundTraceMethodInterceptor inbound) {
    var beans = new StaticListableBeanFactory();
    beans.addBean("outbound", outbound);
    beans.addBean("inbound", inbound);
    return new RabbitTracingBeanPostProcessor(
        beans.getBeanProvider(TracePropagationMessagePostProcessor.class),
        beans.getBeanProvider(InboundTraceMethodInterceptor.class));
  }
}
