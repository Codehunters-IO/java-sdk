package io.codehunters.fileshare.config;

import io.codehunters.fileshare.FileShareException;
import io.codehunters.fileshare.FileShareUnauthorizedException;
import io.codehunters.fileshare.FileShareUnavailableException;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Sends the client-credentials access token of one registration through Spring Security's {@link
 * OAuth2ClientHttpRequestInterceptor}. The token is cached under a fixed principal, not the
 * caller's user, and dropped when the service answers 401. Token failures become {@link
 * FileShareException}s. A manager that returns no authorized client fails the call as unauthorized
 * instead of sending it without a token.
 */
final class OAuth2ClientCredentialsInterceptor implements FileShareAuthInterceptor {

  private static final Set<String> TOKEN_ENDPOINT_DOWN =
      Set.of("server_error", "temporarily_unavailable");

  private final OAuth2ClientHttpRequestInterceptor delegate;

  OAuth2ClientCredentialsInterceptor(
      OAuth2AuthorizedClientManager manager,
      OAuth2AuthorizedClientService clientService,
      FileShareProperties.Auth auth) {
    var principal = UsernamePasswordAuthenticationToken.unauthenticated(auth.principal(), null);
    delegate =
        new OAuth2ClientHttpRequestInterceptor(
            new RequiredClientManager(manager, auth.clientRegistrationId()));
    delegate.setClientRegistrationIdResolver(request -> auth.clientRegistrationId());
    delegate.setPrincipalResolver(request -> principal);
    delegate.setAuthorizationFailureHandler(
        OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(clientService));
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    try {
      return delegate.intercept(request, body, execution);
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
