package io.codehunters.security.authz;

import java.time.Duration;
import java.util.Map;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;

/** Builds Keycloak authorization clients whose HTTP calls cannot hang a request thread. */
final class KeycloakAuthzClients {

  private KeycloakAuthzClients() {}

  static AuthzClient create(
      String url,
      String realm,
      String resource,
      String clientSecret,
      Duration connectTimeout,
      Duration readTimeout) {
    var requestConfig =
        RequestConfig.custom()
            .setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()))
            .setConnectionRequestTimeout(Math.toIntExact(connectTimeout.toMillis()))
            .setSocketTimeout(Math.toIntExact(readTimeout.toMillis()))
            .build();
    var httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
    return AuthzClient.create(
        new Configuration(url, realm, resource, Map.of("secret", clientSecret), httpClient));
  }
}
