package io.codehunters.web.errors.servlet.fixture;

import io.codehunters.tracing.TraceContextPort;
import io.codehunters.tracing.TraceParent;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A tracing SDK port that always reports the same trace. */
@Configuration(proxyBeanMethods = false)
public class FixedTraceContext {

  /** The trace id the port reports. */
  public static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  @Bean
  TraceContextPort traceContextPort() {
    return new TraceContextPort() {
      @Override
      public Optional<String> currentTraceId() {
        return Optional.of(TRACE_ID);
      }

      @Override
      public Optional<String> currentSpanId() {
        return Optional.empty();
      }

      @Override
      public Optional<TraceParent> currentTraceParent() {
        return Optional.empty();
      }
    };
  }
}
