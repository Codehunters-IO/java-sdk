package io.codehunters.sdk.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class SessionIntrospectorTest {

  private static final String TOKEN = "access-token-abc";
  private static final String PATH = "/realms/vitxo/protocol/openid-connect/token/introspect";

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private HttpServer server;
  private final AtomicInteger callCount = new AtomicInteger();

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  private SessionIntrospector startStub(int status, String body, boolean failOpen)
      throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        PATH,
        exchange -> {
          callCount.incrementAndGet();
          byte[] payload = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, payload.length);
          exchange.getResponseBody().write(payload);
          exchange.close();
        });
    server.start();

    var cfg = new KeycloakSecurityProperties();
    cfg.setUrl("http://localhost:" + server.getAddress().getPort());
    cfg.setRealm("vitxo");
    cfg.setClientId("vitxo-auth");
    cfg.setClientSecret("secret");
    cfg.setCacheTtlSeconds(30);
    cfg.setFailOpen(failOpen);
    return new SessionIntrospector(RestClient.create(), jsonMapper, cfg);
  }

  @Test
  void should_returnTrue_when_sessionActive() throws IOException {
    var introspector = startStub(200, "{\"active\":true}", true);

    assertTrue(introspector.isActive(TOKEN));
    assertEquals(1, callCount.get());
  }

  @Test
  void should_returnFalse_when_sessionRevoked() throws IOException {
    var introspector = startStub(200, "{\"active\":false}", true);

    assertFalse(introspector.isActive(TOKEN));
  }

  @Test
  void should_cacheResult_and_notCallKeycloakTwice_withinTtl() throws IOException {
    var introspector = startStub(200, "{\"active\":true}", true);

    assertTrue(introspector.isActive(TOKEN));
    assertTrue(introspector.isActive(TOKEN)); // second call served from cache

    assertEquals(1, callCount.get());
  }

  @Test
  void should_failOpen_when_keycloakErrors() throws IOException {
    var introspector = startStub(500, "boom", true);

    assertTrue(introspector.isActive(TOKEN));
  }

  @Test
  void should_failClosed_when_keycloakErrors_andFailOpenFalse() throws IOException {
    var introspector = startStub(500, "boom", false);

    assertFalse(introspector.isActive(TOKEN));
  }
}
