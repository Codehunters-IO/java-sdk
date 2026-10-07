package io.codehunters.barcode.config;

import io.codehunters.barcode.BarcodeReader;
import io.codehunters.barcode.BarcodeRenderer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers a {@link BarcodeRenderer} and a {@link BarcodeReader} with the configured limits. Each
 * backs off when the application defines its own; {@code codehunters.barcode.enabled=false}
 * disables the SDK.
 */
@AutoConfiguration
@ConditionalOnBooleanProperty(name = "codehunters.barcode.enabled", matchIfMissing = true)
@EnableConfigurationProperties(BarcodeProperties.class)
public class BarcodeAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public BarcodeAutoConfiguration() {}

  /**
   * Renders barcodes within the configured size limits.
   *
   * @param properties the SDK configuration
   * @return the renderer
   */
  @Bean
  @ConditionalOnMissingBean
  public BarcodeRenderer barcodeRenderer(BarcodeProperties properties) {
    return new BarcodeRenderer(properties.limits());
  }

  /**
   * Decodes barcodes from images within the configured pixel limit.
   *
   * @param properties the SDK configuration
   * @return the reader
   */
  @Bean
  @ConditionalOnMissingBean
  public BarcodeReader barcodeReader(BarcodeProperties properties) {
    return new BarcodeReader(properties.limits());
  }
}
