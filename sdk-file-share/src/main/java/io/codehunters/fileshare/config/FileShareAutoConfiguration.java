package io.codehunters.fileshare.config;

import io.codehunters.fileshare.application.FileShareClient;
import io.codehunters.fileshare.infrastructure.RestClientFileShareAdapter;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot auto-configuration that wires the file-share {@link FileShareClient} and its backing
 * {@link RestClient}. Active when {@code vitxo.file-share.enabled} is {@code true} (the default)
 * and {@link RestClient} is on the classpath. Consumers may override either bean to customize
 * behavior.
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@ConditionalOnProperty(
    prefix = "vitxo.file-share",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(FileShareProperties.class)
public class FileShareAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public FileShareAutoConfiguration() {}

  /**
   * Builds the {@link RestClient} used to call the file-share service.
   *
   * @param properties the file-share configuration
   * @return a configured {@link RestClient}
   * @throws IllegalStateException if {@code baseUrl} is missing while the SDK is enabled
   */
  @Bean
  @ConditionalOnMissingBean(name = "fileShareRestClient")
  public RestClient fileShareRestClient(FileShareProperties properties) {
    if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
      throw new IllegalStateException(
          "vitxo.file-share.base-url must be configured when SDK is enabled");
    }
    return RestClient.builder()
        .baseUrl(properties.baseUrl())
        .requestFactory(buildRequestFactory(properties.connectTimeout(), properties.readTimeout()))
        .build();
  }

  /**
   * Builds the default {@link FileShareClient} backed by the given {@link RestClient}.
   *
   * @param fileShareRestClient the REST client to call the file-share service
   * @return the REST-backed {@link FileShareClient}
   */
  @Bean
  @ConditionalOnMissingBean(FileShareClient.class)
  public FileShareClient fileShareClient(RestClient fileShareRestClient) {
    return new RestClientFileShareAdapter(fileShareRestClient);
  }

  private ClientHttpRequestFactory buildRequestFactory(
      Duration connectTimeout, Duration readTimeout) {
    var factory = new JdkClientHttpRequestFactory();
    factory.setReadTimeout(readTimeout);
    return factory;
  }
}
