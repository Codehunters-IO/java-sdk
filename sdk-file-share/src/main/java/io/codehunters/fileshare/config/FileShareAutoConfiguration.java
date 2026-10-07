package io.codehunters.fileshare.config;

import io.codehunters.fileshare.application.FileShareClient;
import io.codehunters.fileshare.infrastructure.PresignedUrlPolicy;
import io.codehunters.fileshare.infrastructure.RestClientFileShareAdapter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
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

  private static final Logger LOG = LoggerFactory.getLogger(FileShareAutoConfiguration.class);
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
   * @param auth the SDK's authentication step, applied after {@code interceptors}, if configured
   * @return a configured {@link RestClient}
   * @throws IllegalStateException if the base URL is missing or invalid, or authentication is
   *     configured but cannot be applied
   */
  @Bean(defaultCandidate = false)
  @ConditionalOnMissingBean(name = "fileShareRestClient")
  public RestClient fileShareRestClient(
      FileShareProperties properties,
      ObjectProvider<RestClient.Builder> builders,
      ObjectProvider<FileShareRequestInterceptor> interceptors,
      ObjectProvider<FileShareAuthInterceptor> auth) {
    var factory = new JdkClientHttpRequestFactory(httpClient(properties.connectTimeout()));
    factory.setReadTimeout(properties.readTimeout());
    return restClient(properties, builders, interceptors(interceptors, auth, properties), factory);
  }

  /**
   * Builds the {@link RestClient} used for content downloads. Its timeout bounds the silence
   * between bytes instead of the whole transfer, and it never follows redirects.
   *
   * @param properties the file-share configuration
   * @param builders the application's builder, if any
   * @param interceptors interceptors applied to every call, in order
   * @param auth the SDK's authentication step, applied after {@code interceptors}, if configured
   * @return a configured {@link RestClient}
   * @throws IllegalStateException if the base URL is missing or invalid, or authentication is
   *     configured but cannot be applied
   */
  @Bean(defaultCandidate = false)
  @ConditionalOnMissingBean(name = "fileShareContentRestClient")
  public RestClient fileShareContentRestClient(
      FileShareProperties properties,
      ObjectProvider<RestClient.Builder> builders,
      ObjectProvider<FileShareRequestInterceptor> interceptors,
      ObjectProvider<FileShareAuthInterceptor> auth) {
    var factory =
        new NoRedirectRequestFactory(properties.connectTimeout(), properties.downloadIdleTimeout());
    return restClient(properties, builders, interceptors(interceptors, auth, properties), factory);
  }

  /**
   * Builds the default {@link FileShareClient}.
   *
   * @param fileShareRestClient the REST client to call the file-share service
   * @param fileShareContentRestClient the REST client for content downloads
   * @param properties the file-share configuration
   * @return the REST-backed {@link FileShareClient}
   */
  @Bean
  @ConditionalOnMissingBean(FileShareClient.class)
  public FileShareClient fileShareClient(
      @Qualifier("fileShareRestClient") RestClient fileShareRestClient,
      @Qualifier("fileShareContentRestClient") RestClient fileShareContentRestClient,
      FileShareProperties properties) {
    var policy = new PresignedUrlPolicy(properties.presigned().allowedHosts());
    if (policy.allowsAnyHost()) {
      LOG.warn(
          "codehunters.file-share.presigned.allowed-hosts is empty:"
              + " presigned URLs from the service may point at any https host");
    }
    return new RestClientFileShareAdapter(
            fileShareRestClient, policy, properties.maxDownloadSize().toBytes())
        .withContentClient(fileShareContentRestClient);
  }

  private static RestClient restClient(
      FileShareProperties properties,
      ObjectProvider<RestClient.Builder> builders,
      List<ClientHttpRequestInterceptor> interceptors,
      ClientHttpRequestFactory factory) {
    return builders
        .getIfAvailable(RestClient::builder)
        .clone()
        .baseUrl(requireBaseUrl(properties))
        .requestFactory(factory)
        .requestInterceptors(list -> list.addAll(interceptors))
        .build();
  }

  private static List<ClientHttpRequestInterceptor> interceptors(
      ObjectProvider<FileShareRequestInterceptor> interceptors,
      ObjectProvider<FileShareAuthInterceptor> auth,
      FileShareProperties properties) {
    FileShareAuthInterceptor authInterceptor = auth.getIfAvailable();
    if (properties.auth().clientRegistrationId() != null && authInterceptor == null) {
      throw new IllegalStateException(
          ClientRegistrationIdCondition.PROPERTY
              + " needs spring-security-oauth2-client on the classpath");
    }
    var all = new ArrayList<ClientHttpRequestInterceptor>(interceptors.orderedStream().toList());
    if (authInterceptor != null) {
      all.add(authInterceptor);
    }
    return all;
  }

  static HttpClient httpClient(Duration connectTimeout) {
    return HttpClient.newBuilder().connectTimeout(connectTimeout).build();
  }

  private static String requireBaseUrl(FileShareProperties properties) {
    String baseUrl = properties.baseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException(BASE_URL + " must be configured when the SDK is enabled");
    }
    URI uri = parseHttp(baseUrl);
    if (properties.auth().clientRegistrationId() != null
        && !"https".equalsIgnoreCase(uri.getScheme())
        && !properties.auth().allowInsecureHttp()) {
      throw new IllegalStateException(
          BASE_URL
              + " must use https when authentication is configured, or the token travels in clear;"
              + " set codehunters.file-share.auth.allow-insecure-http=true to accept that");
    }
    return baseUrl;
  }

  private static URI parseHttp(String baseUrl) {
    try {
      URI uri = new URI(baseUrl);
      boolean isHttp =
          "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
      if (isHttp && uri.getHost() != null) {
        return uri;
      }
    } catch (URISyntaxException e) {
      // reported below with the property name
    }
    throw new IllegalStateException(BASE_URL + " must be an absolute http or https URL");
  }

  /** Client-credentials authentication, when Spring Security's OAuth2 client is present. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(OAuth2AuthorizedClientManager.class)
  @Conditional(ClientRegistrationIdCondition.class)
  static class ClientCredentialsConfiguration {

    @Bean
    FileShareAuthInterceptor fileShareClientCredentialsInterceptor(
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
          properties);
    }
  }
}
