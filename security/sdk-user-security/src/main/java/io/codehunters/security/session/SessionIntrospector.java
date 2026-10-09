package io.codehunters.security.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.codehunters.security.config.KeycloakSecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks Keycloak (RFC 7662 token introspection) whether an access token still belongs to a live
 * session. Signature, issuer and expiry are verified locally before this runs; introspection only
 * closes the gap left by revoked sessions whose tokens have not expired yet.
 *
 * <p>Results are cached by token hash in a Caffeine cache that expires them {@code cache-ttl} after
 * they were written and keeps at most {@code cache-max-entries}. Failed calls are never cached and
 * are counted; {@code failOpen} decides whether a verified token is accepted while Keycloak is
 * unreachable.
 */
public class SessionIntrospector {

  private static final Logger log = LoggerFactory.getLogger(SessionIntrospector.class);

  private final RestClient restClient;
  private final String introspectionUri;
  private final String clientId;
  private final String clientSecret;
  private final boolean failOpen;
  private final Cache<String, Boolean> cache;
  private final LongAdder failures = new LongAdder();

  /**
   * Creates the introspector.
   *
   * @param restClient HTTP client, configured with the introspection timeouts
   * @param introspectionUri the realm's introspection endpoint
   * @param config introspection settings
   * @param clock clock driving cache expiry
   */
  public SessionIntrospector(
      RestClient restClient,
      String introspectionUri,
      KeycloakSecurityProperties.Introspection config,
      Clock clock) {
    this.restClient = restClient;
    this.introspectionUri = introspectionUri;
    this.clientId = config.clientId();
    this.clientSecret = config.clientSecret();
    this.failOpen = config.failOpen();
    this.cache = cache(config, clock);
  }

  private static Cache<String, Boolean> cache(
      KeycloakSecurityProperties.Introspection config, Clock clock) {
    return Caffeine.newBuilder()
        .maximumSize(config.cacheMaxEntries())
        .expireAfterWrite(config.cacheTtl())
        .ticker(() -> Duration.between(Instant.EPOCH, clock.instant()).toNanos())
        .recordStats()
        .build();
  }

  /**
   * The result cache, for metrics and diagnostics.
   *
   * @return the cache, keyed by token hash
   */
  public Cache<String, Boolean> cache() {
    return cache;
  }

  /**
   * Introspection calls that got no answer from Keycloak since startup.
   *
   * @return the number of failed calls
   */
  public long failures() {
    return failures.sum();
  }

  /**
   * Whether the token's Keycloak session is still active.
   *
   * @param token the raw access token
   * @return {@code true} when active, or when Keycloak is unreachable and fail-open is enabled
   */
  public boolean isActive(String token) {
    String key = sha256(token);
    Boolean cached = cache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }
    return introspect(token).map(active -> remember(key, active)).orElse(failOpen);
  }

  private boolean remember(String key, boolean active) {
    cache.put(key, active);
    return active;
  }

  private Optional<Boolean> introspect(String token) {
    try {
      Map<?, ?> body =
          restClient
              .post()
              .uri(introspectionUri)
              .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form(token))
              .retrieve()
              .body(Map.class);
      return Optional.of(body != null && Boolean.TRUE.equals(body.get("active")));
    } catch (RestClientException e) {
      failures.increment();
      log.warn(
          "Keycloak token introspection failed ({}); failing {}",
          e.getClass().getSimpleName(),
          failOpen ? "open" : "closed");
      return Optional.empty();
    }
  }

  private static LinkedMultiValueMap<String, String> form(String token) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("token", token);
    return form;
  }

  private static String sha256(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every Java platform", e);
    }
  }
}
