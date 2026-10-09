package io.codehunters.security.authz.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process stand-in for a Keycloak realm with Authorization Services: serves the JWK set, the UMA
 * discovery document, the token endpoint (client credentials and UMA grants) and the protection
 * API's resource lookup by name. Grants are {@code resourceId#scope} strings set by the test.
 */
final class FakeKeycloak implements AutoCloseable {

  static final String REALM = "test";
  static final String CLIENT_ID = "svc";
  static final String USERS_ID = "users-id";

  private static final String UMA_GRANT = "urn:ietf:params:oauth:grant-type:uma-ticket";

  private final RSAKey signingKey = generateKey();
  private final HttpServer server;
  private final Map<String, String> resourceIds = Map.of("users", USERS_ID);
  private final Set<String> grants = ConcurrentHashMap.newKeySet();
  private final AtomicInteger umaCalls = new AtomicInteger();
  private final AtomicInteger resourceLookups = new AtomicInteger();
  private final AtomicBoolean stopped = new AtomicBoolean();

  FakeKeycloak() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext(realmPath("/protocol/openid-connect/certs"), this::serveJwks);
    server.createContext(realmPath("/.well-known/uma2-configuration"), this::serveDiscovery);
    server.createContext(realmPath("/protocol/openid-connect/token"), this::serveToken);
    server.createContext(realmPath("/authz/protection/resource_set"), this::serveResources);
    server.start();
  }

  String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  String issuer() {
    return url() + "/realms/" + REALM;
  }

  void grant(String resourceId, String scope) {
    grants.add(resourceId + "#" + scope);
  }

  int umaCalls() {
    return umaCalls.get();
  }

  int resourceLookups() {
    return resourceLookups.get();
  }

  String token() {
    Instant now = Instant.now();
    return sign(
        new JWTClaimsSet.Builder()
            .issuer(issuer())
            .subject("user-1")
            .audience(CLIENT_ID)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("preferred_username", "alice")
            .claim("realm_access", Map.of("roles", List.of("user")))
            .build());
  }

  @Override
  public void close() {
    if (stopped.compareAndSet(false, true)) {
      server.stop(0);
    }
  }

  private void serveJwks(HttpExchange exchange) throws IOException {
    respond(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString());
  }

  private void serveDiscovery(HttpExchange exchange) throws IOException {
    String realm = issuer();
    respond(
        exchange,
        200,
        """
        {"issuer":"%1$s","token_endpoint":"%1$s/protocol/openid-connect/token",
         "jwks_uri":"%1$s/protocol/openid-connect/certs",
         "resource_registration_endpoint":"%1$s/authz/protection/resource_set",
         "permission_endpoint":"%1$s/authz/protection/permission",
         "policy_endpoint":"%1$s/authz/protection/uma-policy"}
        """
            .formatted(realm));
  }

  private void serveToken(HttpExchange exchange) throws IOException {
    Map<String, List<String>> form = form(new String(exchange.getRequestBody().readAllBytes()));
    if (!form.getOrDefault("grant_type", List.of()).contains(UMA_GRANT)) {
      respond(exchange, 200, "{\"access_token\":\"" + pat() + "\",\"token_type\":\"Bearer\"}");
      return;
    }
    umaCalls.incrementAndGet();
    List<String> requested = form.getOrDefault("permission", List.of());
    if (!requested.stream().allMatch(this::isGranted)) {
      respond(
          exchange, 403, "{\"error\":\"access_denied\",\"error_description\":\"not_authorized\"}");
      return;
    }
    respond(
        exchange, 200, "{\"access_token\":\"" + rpt(requested) + "\",\"token_type\":\"Bearer\"}");
  }

  private void serveResources(HttpExchange exchange) throws IOException {
    resourceLookups.incrementAndGet();
    Map<String, List<String>> query = form(exchange.getRequestURI().getRawQuery());
    String id = resourceIds.get(query.getOrDefault("name", List.of("")).getFirst());
    respond(exchange, 200, id == null ? "[]" : "[{\"_id\":\"" + id + "\",\"name\":\"users\"}]");
  }

  private boolean isGranted(String permission) {
    String[] parts = permission.split("#", 2);
    return parts.length == 2
        && Arrays.stream(parts[1].split(",")).allMatch(s -> grants.contains(parts[0] + "#" + s));
  }

  /** The protection API token of the resource server; the client parses it to know its expiry. */
  private String pat() {
    Instant now = Instant.now();
    return sign(
        new JWTClaimsSet.Builder()
            .issuer(issuer())
            .subject("service-account-" + CLIENT_ID)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .build());
  }

  private String rpt(List<String> permissions) {
    List<Map<String, Object>> granted =
        permissions.stream()
            .map(p -> p.split("#", 2))
            .<Map<String, Object>>map(
                p -> Map.of("rsid", p[0], "scopes", Arrays.asList(p[1].split(","))))
            .toList();
    return sign(
        new JWTClaimsSet.Builder()
            .issuer(issuer())
            .subject("user-1")
            .claim("authorization", Map.of("permissions", granted))
            .build());
  }

  private static Map<String, List<String>> form(String body) {
    Map<String, List<String>> values = new ConcurrentHashMap<>();
    if (body == null || body.isEmpty()) {
      return values;
    }
    for (String pair : body.split("&")) {
      String[] kv = pair.split("=", 2);
      values
          .computeIfAbsent(decode(kv[0]), k -> new java.util.ArrayList<>())
          .add(kv.length == 2 ? decode(kv[1]) : "");
    }
    return values;
  }

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private String sign(JWTClaimsSet claims) {
    try {
      var jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
              claims);
      jwt.sign(new RSASSASigner(signingKey));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  private static RSAKey generateKey() {
    try {
      return new RSAKeyGenerator(2048).keyID("realm-key").generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
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
