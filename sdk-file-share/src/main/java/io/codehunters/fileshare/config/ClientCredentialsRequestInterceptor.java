package io.codehunters.fileshare.config;

import io.codehunters.fileshare.FileShareException;
import io.codehunters.fileshare.FileShareUnauthorizedException;
import io.codehunters.fileshare.FileShareUnavailableException;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Sends the client-credentials access token of one registration. The token is cached until it
 * expires or the service rejects it with 401 or 403.
 */
final class ClientCredentialsRequestInterceptor implements FileShareAuthInterceptor {

  private static final Set<Integer> REJECTED = Set.of(401, 403);
  private static final Set<String> TOKEN_ENDPOINT_DOWN =
      Set.of("server_error", "temporarily_unavailable");

  private final OAuth2AuthorizedClientManager manager;
  private final OAuth2AuthorizedClientService clientService;
  private final OAuth2AuthorizeRequest authorizeRequest;

  private ClientCredentialsRequestInterceptor(
      OAuth2AuthorizedClientManager manager,
      OAuth2AuthorizedClientService clientService,
      FileShareProperties.Auth auth) {
    this.manager = manager;
    this.clientService = clientService;
    this.authorizeRequest =
        OAuth2AuthorizeRequest.withClientRegistrationId(auth.clientRegistrationId())
            .principal(auth.principal())
            .build();
  }

  static ClientCredentialsRequestInterceptor create(
      ClientRegistrationRepository repository,
      OAuth2AuthorizedClientService clientService,
      FileShareProperties properties) {
    requireClientCredentials(repository, properties.auth().clientRegistrationId());
    var manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(repository, clientService);
    manager.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials(c -> c.accessTokenResponseClient(tokenClient(properties)))
            .build());
    return new ClientCredentialsRequestInterceptor(manager, clientService, properties.auth());
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    request.getHeaders().setBearerAuth(accessToken());
    ClientHttpResponse response = execution.execute(request, body);
    if (REJECTED.contains(response.getStatusCode().value())) {
      clientService.removeAuthorizedClient(
          authorizeRequest.getClientRegistrationId(), authorizeRequest.getPrincipal().getName());
    }
    return response;
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

  private String accessToken() {
    try {
      OAuth2AuthorizedClient client = manager.authorize(authorizeRequest);
      if (client == null) {
        throw new FileShareUnauthorizedException("No access token for file-share");
      }
      return client.getAccessToken().getTokenValue();
    } catch (OAuth2AuthorizationException e) {
      throw toFileShareException(e);
    }
  }

  private static FileShareException toFileShareException(OAuth2AuthorizationException e) {
    String message =
        "Could not obtain an access token for file-share: " + e.getError().getErrorCode();
    if (isTransportFailure(e) || TOKEN_ENDPOINT_DOWN.contains(e.getError().getErrorCode())) {
      return new FileShareUnavailableException(message, e);
    }
    return new FileShareUnauthorizedException(message, e);
  }

  private static boolean isTransportFailure(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ResourceAccessException) {
        return true;
      }
    }
    return false;
  }
}
