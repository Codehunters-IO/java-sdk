package io.codehunters.fileshare.config;

import io.codehunters.fileshare.application.FileShareClient;
import io.codehunters.fileshare.infrastructure.PresignedUrlPolicy;
import io.codehunters.fileshare.infrastructure.RestClientFileShareAdapter;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/**
 * Wires the file-share {@link FileShareClient}. Active when {@code codehunters.file-share.enabled}
 * is {@code true} (the default) and {@link RestClient} is on the classpath. The SDK's own {@link
 * RestClient} is not an autowiring candidate, so it never competes with the application's.
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@ConditionalOnBooleanProperty(name = "codehunters.file-share.enabled", matchIfMissing = true)
@EnableConfigurationProperties(FileShareProperties.class)
public class FileShareAutoConfiguration {

  private static final String BASE_URL = "codehunters.file-share.base-url";

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public FileShareAutoConfiguration() {}

  /**
   * Builds the {@link RestClient} used to call the file-share service, starting from the
   * application's {@link RestClient.Builder} when there is one.
   *
   * @param properties the file-share configuration
   * @param builders the application's builder, if any
   * @param interceptors interceptors applied to every call, in order
   * @return a configured {@link RestClient}
   * @throws IllegalStateException if the base URL is missing or invalid, or authentication is
   *     configured but cannot be applied
   */
  @Bean(defaultCandidate = false)
  @ConditionalOnMissingBean(name = "fileShareRestClient")
  public RestClient fileShareRestClient(
      FileShareProperties properties,
      ObjectProvider<RestClient.Builder> builders,
      ObjectProvider<FileShareRequestInterceptor> interceptors) {
    List<FileShareRequestInterceptor> ordered = interceptors.orderedStream().toList();
    requireAuthApplied(properties, ordered);
    var factory = new JdkClientHttpRequestFactory(httpClient(properties.connectTimeout()));
    factory.setReadTimeout(properties.readTimeout());
    return builders
        .getIfAvailable(RestClient::builder)
        .clone()
        .baseUrl(requireBaseUrl(properties.baseUrl()))
        .requestFactory(factory)
        .requestInterceptors(list -> list.addAll(ordered))
        .build();
  }

  /**
   * Builds the default {@link FileShareClient}.
   *
   * @param fileShareRestClient the REST client to call the file-share service
   * @param properties the file-share configuration
   * @return the REST-backed {@link FileShareClient}
   */
  @Bean
  @ConditionalOnMissingBean(FileShareClient.class)
  public FileShareClient fileShareClient(
      @Qualifier("fileShareRestClient") RestClient fileShareRestClient,
      FileShareProperties properties) {
    return new RestClientFileShareAdapter(
        fileShareRestClient,
        new PresignedUrlPolicy(properties.presigned().allowedHosts()),
        properties.maxDownloadSize().toBytes());
  }

  static HttpClient httpClient(Duration connectTimeout) {
    return HttpClient.newBuilder().connectTimeout(connectTimeout).build();
  }

  private static String requireBaseUrl(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException(BASE_URL + " must be configured when the SDK is enabled");
    }
    URI uri = URI.create(baseUrl);
    if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())
        || uri.getHost() == null) {
      throw new IllegalStateException(BASE_URL + " must be an absolute http or https URL");
    }
    return baseUrl;
  }

  private static void requireAuthApplied(
      FileShareProperties properties, List<FileShareRequestInterceptor> interceptors) {
    if (properties.auth().clientRegistrationId() != null && interceptors.isEmpty()) {
      throw new IllegalStateException(
          "codehunters.file-share.auth.client-registration-id needs"
              + " spring-security-oauth2-client on the classpath");
    }
  }

  /** Client-credentials authentication, when Spring Security's OAuth2 client is present. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(OAuth2AuthorizedClientManager.class)
  @ConditionalOnProperty("codehunters.file-share.auth.client-registration-id")
  static class ClientCredentialsConfiguration {

    @Bean
    @ConditionalOnMissingBean(FileShareRequestInterceptor.class)
    FileShareRequestInterceptor fileShareClientCredentialsInterceptor(
        FileShareProperties properties,
        ObjectProvider<ClientRegistrationRepository> repositories,
        ObjectProvider<OAuth2AuthorizedClientService> clientServices) {
      ClientRegistrationRepository repository = repositories.getIfAvailable();
      if (repository == null) {
        throw new IllegalStateException(
            "codehunters.file-share.auth.client-registration-id needs a"
                + " ClientRegistrationRepository bean (spring.security.oauth2.client.registration)");
      }
      return ClientCredentialsRequestInterceptor.create(
          repository,
          clientServices.getIfAvailable(
              () -> new InMemoryOAuth2AuthorizedClientService(repository)),
          properties.auth());
    }
  }
}
