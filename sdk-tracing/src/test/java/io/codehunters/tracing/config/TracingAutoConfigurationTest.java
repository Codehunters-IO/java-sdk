package io.codehunters.tracing.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.tracing.MdcTaskDecorator;
import io.codehunters.tracing.MdcTraceContextAdapter;
import io.codehunters.tracing.TraceContextPort;
import io.codehunters.tracing.TraceParent;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TracingAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TracingAutoConfiguration.class));

  @Test
  void should_register_mdc_port_and_task_decorator() {
    runner.run(
        context -> {
          assertThat(context.getBean(TraceContextPort.class))
              .isInstanceOf(MdcTraceContextAdapter.class);
          assertThat(context).hasSingleBean(MdcTaskDecorator.class);
        });
  }

  @Test
  void should_keep_application_port_when_one_is_defined() {
    TraceContextPort custom = new FixedTraceContext();

    runner
        .withBean(TraceContextPort.class, () -> custom)
        .run(context -> assertThat(context.getBean(TraceContextPort.class)).isSameAs(custom));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("codehunters.tracing.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(TraceContextPort.class));
  }

  private static final class FixedTraceContext implements TraceContextPort {
    @Override
    public Optional<String> currentTraceId() {
      return Optional.empty();
    }

    @Override
    public Optional<String> currentSpanId() {
      return Optional.empty();
    }

    @Override
    public Optional<TraceParent> currentTraceParent() {
      return Optional.empty();
    }
  }
}
