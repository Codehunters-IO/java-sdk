package io.codehunters.fileshare.config;

import java.util.Optional;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Builds the client-credentials step from Spring Security's OAuth2 client: the application's {@link
 * OAuth2AuthorizedClientManager} when it has one that works outside an HTTP request, the SDK's own
 * otherwise.
 */
final class ClientCredentials {

  /**
   * Spring Security's servlet manager: it fails outside an HTTP request (schedulers, listeners).
   */
  private static final String REQUEST_BOUND_MANAGER =
      "org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager";

  private ClientCredentials() {}

  static FileShareAuthInterceptor interceptor(FileShareProperties properties, BeanFactory beans) {
    ClientRegistrationRepository repository = requireRepository(beans);
    requireClientCredentials(repository, properties.auth().clientRegistrationId());
    OAuth2AuthorizedClientService clientService =
        beans
            .getBeanProvider(OAuth2AuthorizedClientService.class)
            .getIfAvailable(() -> new InMemoryOAuth2AuthorizedClientService(repository));
    OAuth2AuthorizedClientManager manager =
        applicationManager(beans)
            .orElseGet(() -> sdkManager(repository, clientService, properties));
    return new OAuth2ClientCredentialsInterceptor(manager, clientService, properties.auth());
  }

  private static ClientRegistrationRepository requireRepository(BeanFactory beans) {
    ClientRegistrationRepository repository =
        beans.getBeanProvider(ClientRegistrationRepository.class).getIfAvailable();
    if (repository == null) {
      throw new IllegalStateException(
          ClientRegistrationIdCondition.PROPERTY
              + " needs a ClientRegistrationRepository bean (spring.security.oauth2.client.registration)");
    }
    return repository;
  }

  private static void requireClientCredentials(
      ClientRegistrationRepository repository, String registrationId) {
    ClientRegistration registration = repository.findByRegistrationId(registrationId);
    if (registration == null) {
      throw new IllegalStateException("No OAuth2 client registration named " + registrationId);
    }
    if (!AuthorizationGrantType.CLIENT_CREDENTIALS.equals(
        registration.getAuthorizationGrantType())) {
      throw new IllegalStateException(
          "OAuth2 client registration " + registrationId + " must use client_credentials");
    }
  }

  private static Optional<OAuth2AuthorizedClientManager> applicationManager(BeanFactory beans) {
    return Optional.ofNullable(
            beans.getBeanProvider(OAuth2AuthorizedClientManager.class).getIfUnique())
        .filter(manager -> !REQUEST_BOUND_MANAGER.equals(manager.getClass().getName()));
  }

  private static OAuth2AuthorizedClientManager sdkManager(
      ClientRegistrationRepository repository,
      OAuth2AuthorizedClientService clientService,
      FileShareProperties properties) {
    var manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(repository, clientService);
    manager.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials(c -> c.accessTokenResponseClient(tokenClient(properties)))
            .build());
    return manager;
  }

  // Spring Security's default token client has no timeouts: a hung token endpoint would block
  // every file-share call.
  private static RestClientClientCredentialsTokenResponseClient tokenClient(
      FileShareProperties properties) {
    var factory =
        new JdkClientHttpRequestFactory(
            FileShareAutoConfiguration.httpClient(properties.connectTimeout()));
    factory.setReadTimeout(properties.readTimeout());
    var client = new RestClientClientCredentialsTokenResponseClient();
    client.setRestClient(
        RestClient.builder()
            .requestFactory(factory)
            .configureMessageConverters(
                converters ->
                    converters
                        .addCustomConverter(new FormHttpMessageConverter())
                        .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
            .build());
    return client;
  }
}
