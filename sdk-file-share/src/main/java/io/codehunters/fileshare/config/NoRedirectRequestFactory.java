package io.codehunters.fileshare.config;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Request factory for content downloads. Its read timeout applies to each read, so it bounds the
 * silence between bytes rather than the whole transfer. Redirects are never followed: they would
 * resend the bearer token to another location.
 */
final class NoRedirectRequestFactory extends SimpleClientHttpRequestFactory {

  NoRedirectRequestFactory(Duration connectTimeout, Duration idleTimeout) {
    setConnectTimeout(connectTimeout);
    setReadTimeout(idleTimeout);
  }

  @Override
  protected void prepareConnection(HttpURLConnection connection, String httpMethod)
      throws IOException {
    super.prepareConnection(connection, httpMethod);
    connection.setInstanceFollowRedirects(false);
  }
}
