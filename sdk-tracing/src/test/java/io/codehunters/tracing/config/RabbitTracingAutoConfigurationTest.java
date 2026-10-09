package io.codehunters.tracing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.codehunters.tracing.amqp.InboundTraceMethodInterceptor;
import io.codehunters.tracing.amqp.RabbitTracingBeanPostProcessor;
import io.codehunters.tracing.amqp.TracePropagationMessagePostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class RabbitTracingAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(RabbitTracingAutoConfiguration.class));

  @Test
  void should_register_rabbit_tracing_without_an_application_json_mapper() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(TracePropagationMessagePostProcessor.class);
          assertThat(context).hasSingleBean(InboundTraceMethodInterceptor.class);
          assertThat(context).hasSingleBean(RabbitTracingBeanPostProcessor.class);
        });
  }

  @Test
  void should_add_trace_headers_to_application_rabbit_template_once() {
    runner
        .withBean(RabbitTemplate.class, () -> new RabbitTemplate(mock(ConnectionFactory.class)))
        .run(
            context ->
                assertThat(context.getBean(RabbitTemplate.class).getBeforePublishPostProcessors())
                    .singleElement()
                    .isInstanceOf(TracePropagationMessagePostProcessor.class));
  }

  @Test
  void should_register_nothing_without_jackson() {
    runner
        .withClassLoader(new FilteredClassLoader(JsonMapper.class))
        .run(context -> assertThat(context).doesNotHaveBean(RabbitTracingBeanPostProcessor.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("codehunters.tracing.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(RabbitTracingBeanPostProcessor.class));
  }
}
