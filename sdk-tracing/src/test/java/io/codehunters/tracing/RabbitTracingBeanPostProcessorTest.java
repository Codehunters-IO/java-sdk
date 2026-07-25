package io.codehunters.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

class RabbitTracingBeanPostProcessorTest {

  private TracePropagationMessagePostProcessor outbound;
  private InboundTraceMethodInterceptor inbound;
  private RabbitTracingBeanPostProcessor processor;

  @BeforeEach
  void setUp() {
    outbound = new TracePropagationMessagePostProcessor(JsonMapper.builder().build());
    inbound = new InboundTraceMethodInterceptor();
    processor = new RabbitTracingBeanPostProcessor(outbound, inbound);
  }

  @Test
  @DisplayName("should_add_outbound_post_processor_when_bean_is_RabbitTemplate")
  void should_add_outbound_to_rabbit_template() {
    RabbitTemplate template = mock(RabbitTemplate.class);

    Object result = processor.postProcessAfterInitialization(template, "rabbitTemplate");

    assertThat(result).isSameAs(template);
    verify(template).addBeforePublishPostProcessors(any());
  }

  @Test
  @DisplayName("should_install_inbound_interceptor_when_listener_factory_has_no_advice")
  void should_install_inbound_when_no_existing_advice() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();

    processor.postProcessAfterInitialization(factory, "factory");

    Advice[] chain = factory.getAdviceChain();
    assertThat(chain).containsExactly(inbound);
  }

  @Test
  @DisplayName("should_append_inbound_interceptor_when_existing_advice_present")
  void should_append_inbound_when_existing_advice() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    Advice existing = mock(Advice.class);
    factory.setAdviceChain(existing);

    processor.postProcessAfterInitialization(factory, "factory");

    Advice[] chain = factory.getAdviceChain();
    assertThat(chain).containsExactly(existing, inbound);
  }

  @Test
  @DisplayName("should_return_bean_unchanged_when_neither_template_nor_factory")
  void should_passthrough_other_beans() {
    Object bean = new Object();

    Object result = processor.postProcessAfterInitialization(bean, "x");

    assertThat(result).isSameAs(bean);
  }
}
