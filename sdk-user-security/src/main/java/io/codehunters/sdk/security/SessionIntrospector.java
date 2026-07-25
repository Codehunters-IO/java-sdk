package io.codehunters.sdk.security;

import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates that an access token still belongs to a live Keycloak session via RFC 7662 token
 * introspection. Because access tokens are stateless JWTs, revoking a session (e.g. single-active-
 * session enforcement on a new login) does not invalidate tokens already issued for it;
 * introspection asks Keycloak whether the token is still {@code active} so revoked-session tokens
 * can be rejected.
 *
 * <p>Results are cached for a short TTL to bound Keycloak load; invalidation latency is therefore
 * at most the configured TTL. On introspection failure the {@code failOpen} flag decides whether
 * the request is allowed (availability) or rejected (security).
 */
public class SessionIntrospector {

  private static final Logger log = LoggerFactory.getLogger(SessionIntrospector.class);
  private static final int MAX_CACHE_ENTRIES = 10_000;

  private final RestClient restClient;
  private final JsonMapper jsonMapper;
  private final String introspectionUri;
  private final String clientId;
  private final String clientSecret;
  private final long cacheTtlMillis;
  private final boolean failOpen;
  private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

  /**
   * Creates the introspector.
   *
   * @param restClient the HTTP client used to call Keycloak
   * @param jsonMapper the JSON mapper used to parse the introspection response
   * @param config the session-validation configuration
   */
  public SessionIntrospector(
      RestClient restClient, JsonMapper jsonMapper, KeycloakSecurityProperties config) {
    this.restClient = restClient;
    this.jsonMapper = jsonMapper;
    this.introspectionUri = config.getIntrospectionUri();
    this.clientId = config.getClientId();
    this.clientSecret = config.getClientSecret();
    this.cacheTtlMillis = config.getCacheTtlSeconds() * 1000L;
    this.failOpen = config.isFailOpen();
  }

  /**
   * Whether the token still belongs to an active Keycloak session.
   *
   * @param token the raw access token (JWT) from the {@code Authorization} header
   * @return {@code true} if the session is active (or fail-open on error); {@code false} if revoked
   */
  public boolean isActive(String token) {
    long now = System.currentTimeMillis();
    CacheEntry cached = cache.get(token);
    if (cached != null && cached.expiresAt > now) {
      return cached.active;
    }
    boolean active = introspect(token);
    put(token, active, now);
    return active;
  }

  private boolean introspect(String token) {
    try {
      var form = new LinkedMultiValueMap<String, String>();
      form.add("token", token);
      String body =
          restClient
              .post()
              .uri(introspectionUri)
              .headers(h -> h.setBasicAuth(clientId, clientSecret))
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(String.class);
      JsonNode node = jsonMapper.readTree(body);
      return node.path("active").asBoolean(false);
    } catch (Exception e) {
      log.warn(
          "Token introspection failed ({}); failing {}",
          e.getMessage(),
          failOpen ? "open" : "closed");
      return failOpen;
    }
  }

  private void put(String token, boolean active, long now) {
    if (cache.size() >= MAX_CACHE_ENTRIES) {
      cache.entrySet().removeIf(e -> e.getValue().expiresAt <= now);
    }
    cache.put(token, new CacheEntry(active, now + cacheTtlMillis));
  }

  private record CacheEntry(boolean active, long expiresAt) {}
}
