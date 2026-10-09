package io.codehunters.measure.config;

import io.codehunters.measure.Measured;
import io.codehunters.measure.internal.MeasureAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for {@link Measured}. Registers the {@link MeasureAspect} once
 * Boot's metrics auto-configuration has provided a {@link MeterRegistry}, and validates every
 * {@code @Measured} annotation at startup.
 *
 * <p>The aspect is applied by Boot's AOP auto-configuration ({@code spring.aop.auto}, on by default
 * with AspectJ on the classpath); this auto-configuration does not enable proxying itself. Set
 * {@code codehunters.measure.enabled=false} to turn it off.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
      "org.springframework.boot.micrometer.metrics.autoconfigure"
          + ".CompositeMeterRegistryAutoConfiguration"
    })
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBooleanProperty(name = "codehunters.measure.enabled", matchIfMissing = true)
@EnableConfigurationProperties(MeasureProperties.class)
public class MeasureAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public MeasureAutoConfiguration() {}

  /**
   * Registers the measurement aspect.
   *
   * @param registry the application's meter registry
   * @param properties the measure configuration
   * @return the {@link MeasureAspect}
   */
  @Bean
  @ConditionalOnSingleCandidate(MeterRegistry.class)
  @ConditionalOnMissingBean
  public MeasureAspect measureAspect(MeterRegistry registry, MeasureProperties properties) {
    return new MeasureAspect(registry, properties.maxTagValues());
  }

  /**
   * Validates {@link Measured} annotations on every bean at startup.
   *
   * @return the validator
   */
  @Bean
  @ConditionalOnMissingBean
  public static MeasuredBeanValidator measuredBeanValidator() {
    return new MeasuredBeanValidator();
  }
}
