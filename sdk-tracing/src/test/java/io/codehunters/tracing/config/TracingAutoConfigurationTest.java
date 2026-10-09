package io.codehunters.tracing.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.tracing.TraceContextPort;
import io.codehunters.tracing.TraceParent;
import io.codehunters.tracing.fixture.OtelTracers;
import io.codehunters.tracing.mdc.MdcTraceContextAdapter;
import io.codehunters.tracing.micrometer.MicrometerTraceContextAdapter;
import io.micrometer.tracing.Tracer;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TracingAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TracingAutoConfiguration.class));

  @Test
  void should_back_the_port_with_the_tracer_when_a_tracer_bean_exists() {
    runner
        .withBean(Tracer.class, OtelTracers::sampling)
        .run(
            context ->
                assertThat(context.getBean(TraceContextPort.class))
                    .isInstanceOf(MicrometerTraceContextAdapter.class));
  }

  @Test
  void should_back_the_port_with_the_mdc_when_no_tracer_bean_exists() {
    runner.run(
        context ->
            assertThat(context.getBean(TraceContextPort.class))
                .isInstanceOf(MdcTraceContextAdapter.class));
  }

  @Test
  void should_back_the_port_with_the_mdc_when_the_tracer_is_the_no_op_tracer() {
    runner
        .withBean(Tracer.class, () -> Tracer.NOOP)
        .run(
            context ->
                assertThat(context.getBean(TraceContextPort.class))
                    .isInstanceOf(MdcTraceContextAdapter.class));
  }

  @Test
  void should_keep_the_application_port_when_one_is_defined() {
    TraceContextPort custom = new FixedTraceContext();

    runner
        .withBean(Tracer.class, OtelTracers::sampling)
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
