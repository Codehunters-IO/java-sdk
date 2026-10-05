package io.codehunters.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * In-process stand-in for a Keycloak realm: serves the JWK set and the token introspection
 * endpoint, and signs access tokens with its own RSA key. Tokens default to a valid user with the
 * {@code user} realm role; tests override single claims through the builder.
 */
final class FakeKeycloak implements AutoCloseable {

  static final String REALM = "test";
  static final String USER_ID = "user-1";
  static final String CLIENT_ID = "svc";

  private final RSAKey signingKey = generateKey("realm-key");
  private final RSAKey foreignKey = generateKey("realm-key");
  private final HttpServer server;
  private final AtomicInteger introspectionCalls = new AtomicInteger();
  private volatile int introspectionStatus = 200;
  private volatile boolean sessionActive = true;

  FakeKeycloak() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext(realmPath("/protocol/openid-connect/certs"), this::serveJwks);
    server.createContext(
        realmPath("/protocol/openid-connect/token/introspect"), this::serveIntrospection);
    server.start();
  }

  String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  String issuer() {
    return url() + "/realms/" + REALM;
  }

  String introspectionUri() {
    return issuer() + "/protocol/openid-connect/token/introspect";
  }

  int introspectionCalls() {
    return introspectionCalls.get();
  }

  void sessionActive(boolean active) {
    this.sessionActive = active;
  }

  void introspectionFailsWith(int status) {
    this.introspectionStatus = status;
  }

  String token() {
    return token(claims -> {});
  }

  String token(Consumer<JWTClaimsSet.Builder> customizer) {
    return sign(signingKey, claims(customizer));
  }

  /** A well-formed token signed with a key the realm never published. */
  String foreignToken() {
    return sign(foreignKey, claims(claims -> {}));
  }

  /** An {@code alg: none} token carrying otherwise valid claims. */
  String unsignedToken() {
    return new PlainJWT(claims(claims -> {})).serialize();
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private JWTClaimsSet claims(Consumer<JWTClaimsSet.Builder> customizer) {
    Instant now = Instant.now();
    var builder =
        new JWTClaimsSet.Builder()
            .issuer(issuer())
            .subject(USER_ID)
            .audience(CLIENT_ID)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("preferred_username", "alice")
            .claim("sid", "session-1")
            .claim("realm_access", Map.of("roles", List.of("user")));
    customizer.accept(builder);
    return builder.build();
  }

  private static String sign(RSAKey key, JWTClaimsSet claims) {
    try {
      var jwt =
          new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  private static RSAKey generateKey(String keyId) {
    try {
      return new RSAKeyGenerator(2048).keyID(keyId).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  private void serveJwks(HttpExchange exchange) throws IOException {
    respond(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString());
  }

  private void serveIntrospection(HttpExchange exchange) throws IOException {
    introspectionCalls.incrementAndGet();
    exchange.getRequestBody().readAllBytes();
    if (introspectionStatus != 200) {
      respond(exchange, introspectionStatus, "{\"error\":\"server_error\"}");
      return;
    }
    respond(exchange, 200, "{\"active\":" + sessionActive + "}");
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, payload.length);
    exchange.getResponseBody().write(payload);
    exchange.close();
  }

  private static String realmPath(String suffix) {
    return "/realms/" + REALM + suffix;
  }
}
