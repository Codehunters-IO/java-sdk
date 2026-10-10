package io.codehunters.fileshare.config;

import io.codehunters.fileshare.FileShareUnauthorizedException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Refuses to let a call leave without a token: Spring Security's interceptor sends the request
 * unauthenticated when the manager returns no authorized client, which the service would only
 * reject later with a less telling error. The first such miss is logged with the registration id.
 */
final class RequiredClientManager implements OAuth2AuthorizedClientManager {

  private static final Logger LOG = LoggerFactory.getLogger(RequiredClientManager.class);

  private final OAuth2AuthorizedClientManager delegate;
  private final String registrationId;
  private final AtomicBoolean warned = new AtomicBoolean();

  RequiredClientManager(OAuth2AuthorizedClientManager delegate, String registrationId) {
    this.delegate = delegate;
    this.registrationId = registrationId;
  }

  @Override
  public OAuth2AuthorizedClient authorize(OAuth2AuthorizeRequest request) {
    OAuth2AuthorizedClient client = delegate.authorize(request);
    if (client == null) {
      throw noClient();
    }
    return client;
  }

  private FileShareUnauthorizedException noClient() {
    if (warned.compareAndSet(false, true)) {
      LOG.warn(
          "The OAuth2AuthorizedClientManager returned no authorized client for registration {};"
              + " file-share calls fail as unauthorized until it does",
          registrationId);
    }
    return new FileShareUnauthorizedException(
        "No access token for file-share: registration " + registrationId + " was not authorized");
  }
}
