package io.codehunters.totp.config;

import io.codehunters.totp.TotpConfig;
import io.codehunters.totp.TotpGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration exposing a {@link TotpGenerator} bean built from {@link
 * TotpProperties}. Consumers may override the bean to customize behavior.
 */
@AutoConfiguration
@EnableConfigurationProperties(TotpProperties.class)
public class TotpAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public TotpAutoConfiguration() {}

  /**
   * Builds the default {@link TotpGenerator} from the bound properties.
   *
   * @param properties the TOTP configuration
   * @return a configured {@link TotpGenerator}
   */
  @Bean
  @ConditionalOnMissingBean
  public TotpGenerator totpGenerator(TotpProperties properties) {
    var config = new TotpConfig(properties.timeStep(), properties.digits(), properties.algorithm());
    return new TotpGenerator(config);
  }
}
