package io.codehunters.security.authz.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KeycloakAuthzClientsTest {

  // Accepts connections and never answers: a hung Keycloak.
  private final ServerSocket hungKeycloak = openSocket();

  @AfterEach
  void tearDown() throws IOException {
    hungKeycloak.close();
  }

  @Test
  void should_give_up_within_read_timeout_when_keycloak_hangs() {
    String url = "http://127.0.0.1:" + hungKeycloak.getLocalPort();

    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () ->
            assertThatThrownBy(
                    () ->
                        KeycloakAuthzClients.create(
                            url,
                            "test",
                            "svc",
                            "secret",
                            Duration.ofMillis(500),
                            Duration.ofMillis(500)))
                .isInstanceOf(RuntimeException.class));
  }

  private static ServerSocket openSocket() {
    try {
      return new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
