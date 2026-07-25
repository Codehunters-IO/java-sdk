package io.codehunters.measure;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Spring Boot auto-configuration enabling the {@link Measured} aspect. Registers the {@link
 * MeasureAspect} when a {@link MeterRegistry} bean is present.
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@ConditionalOnClass(MeterRegistry.class)
public class MeasureAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public MeasureAutoConfiguration() {}

  /**
   * Registers the measurement aspect.
   *
   * @param registry the Micrometer meter registry
   * @return the {@link MeasureAspect}
   */
  @Bean
  @ConditionalOnBean(MeterRegistry.class)
  public MeasureAspect measureAspect(MeterRegistry registry) {
    return new MeasureAspect(registry);
  }
}
