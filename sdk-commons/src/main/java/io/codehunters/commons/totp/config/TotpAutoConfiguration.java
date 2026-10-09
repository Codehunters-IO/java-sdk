package io.codehunters.commons.totp.config;

import io.codehunters.commons.totp.TotpConfig;
import io.codehunters.commons.totp.TotpGenerator;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration exposing a {@link TotpGenerator} bean built from {@link
 * TotpProperties}. Opt-in: it applies only with {@code codehunters.totp.enabled=true}, so an
 * application that only uses the other sdk-commons helpers gets no TOTP bean. Consumers may
 * override the bean to customize behavior.
 */
@AutoConfiguration
@ConditionalOnBooleanProperty(name = "codehunters.totp.enabled")
@EnableConfigurationProperties(TotpProperties.class)
public class TotpAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public TotpAutoConfiguration() {}

  /**
   * Builds the default {@link TotpGenerator} from the bound properties. It reads the time from the
   * application's {@link Clock} bean when there is exactly one, and from the UTC system clock
   * otherwise.
   *
   * @param properties the TOTP configuration
   * @param clock the application's clock, if any
   * @return a configured {@link TotpGenerator}
   */
  @Bean
  @ConditionalOnMissingBean
  public TotpGenerator totpGenerator(TotpProperties properties, ObjectProvider<Clock> clock) {
    var config = new TotpConfig(properties.timeStep(), properties.digits(), properties.algorithm());
    return new TotpGenerator(config, clock.getIfUnique(Clock::systemUTC));
  }
}
