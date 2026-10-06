package io.codehunters.fileshare.config;

import io.codehunters.fileshare.domain.exception.FileShareUnauthorizedException;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

/** Sends the client-credentials access token of one registration; the manager caches it. */
final class ClientCredentialsRequestInterceptor implements FileShareRequestInterceptor {

  private final OAuth2AuthorizedClientManager manager;
  private final OAuth2AuthorizeRequest authorizeRequest;

  static ClientCredentialsRequestInterceptor create(
      ClientRegistrationRepository repository,
      OAuth2AuthorizedClientService clientService,
      FileShareProperties.Auth auth) {
    requireClientCredentials(repository, auth.clientRegistrationId());
    var manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(repository, clientService);
    manager.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
    return new ClientCredentialsRequestInterceptor(
        manager, auth.clientRegistrationId(), auth.principal());
  }

  ClientCredentialsRequestInterceptor(
      OAuth2AuthorizedClientManager manager, String registrationId, String principal) {
    this.manager = manager;
    this.authorizeRequest =
        OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
            .principal(principal)
            .build();
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    request.getHeaders().setBearerAuth(accessToken());
    return execution.execute(request, body);
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
      throw new FileShareUnauthorizedException(
          "Could not obtain an access token for file-share: " + e.getError().getErrorCode(), e);
    }
  }
}
