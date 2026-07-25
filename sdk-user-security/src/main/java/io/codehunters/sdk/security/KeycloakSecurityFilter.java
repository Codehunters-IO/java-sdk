package io.codehunters.sdk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Servlet filter that trusts the {@code x-user-*} and {@code x-geo-*} headers stamped by the API
 * gateway to populate a {@link KeycloakPrincipal} in the {@link SecurityContextHolder}.
 *
 * <p>When both an {@code Authorization: Bearer} header and an {@code x-user-id} header are present,
 * the filter cross-checks the JWT {@code sub} claim against the header and rejects the request with
 * HTTP 401 on mismatch. The security context is cleared after the chain completes.
 *
 * <p>Trust assumption: only deploy behind a gateway that strips these headers from inbound traffic
 * and injects them itself after authenticating the caller.
 */
public class KeycloakSecurityFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(KeycloakSecurityFilter.class);
  private static final String HEADER_USER_ID = "x-user-id";
  private static final String HEADER_USERNAME = "x-username";
  private static final String HEADER_USER_ROLES = "x-user-roles";
  private static final String HEADER_USER_SID = "x-user-sid";
  private static final String HEADER_GEO_COUNTRY = "x-geo-country";
  private static final String HEADER_GEO_CITY = "x-geo-city";
  private static final String HEADER_GEO_LATITUDE = "x-geo-latitude";
  private static final String HEADER_GEO_LONGITUDE = "x-geo-longitude";
  private static final String HEADER_GEO_IP = "x-geo-ip";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JsonMapper jsonMapper;
  private final List<String> excludedPaths;
  private final SessionIntrospector sessionIntrospector;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  /**
   * Creates the filter without per-request session validation.
   *
   * @param jsonMapper the JSON mapper used to decode JWT payloads
   * @param excludedPaths Ant path patterns the filter skips
   */
  public KeycloakSecurityFilter(JsonMapper jsonMapper, List<String> excludedPaths) {
    this(jsonMapper, excludedPaths, null);
  }

  /**
   * Creates the filter.
   *
   * @param jsonMapper the JSON mapper used to decode JWT payloads
   * @param excludedPaths Ant path patterns the filter skips
   * @param sessionIntrospector validates the token still belongs to a live Keycloak session; {@code
   *     null} disables per-request session validation
   */
  public KeycloakSecurityFilter(
      JsonMapper jsonMapper, List<String> excludedPaths, SessionIntrospector sessionIntrospector) {
    this.jsonMapper = jsonMapper;
    this.excludedPaths = excludedPaths;
    this.sessionIntrospector = sessionIntrospector;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return excludedPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String headerUserId = request.getHeader(HEADER_USER_ID);

    // No gateway-stamped identity: nothing to trust here. Let the security chain decide (401 for
    // protected paths, permitAll for public/excluded ones).
    if (headerUserId == null) {
      filterChain.doFilter(request, response);
      return;
    }

    // Identity headers present but no bearer token: reject. Authenticating on the x-user-* headers
    // alone would let any caller that reaches the service outside the gateway spoof a user by
    // setting x-user-id + x-user-roles. The token is required as proof the gateway authenticated
    // the caller (its signature/validity is verified at the edge and, when enabled, re-checked by
    // the session introspector below).
    String token = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
    if (token == null) {
      unauthorized(response, "Missing bearer token for identity headers");
      return;
    }

    String tokenUserId = extractUserIdFromToken(token);
    if (tokenUserId == null || !tokenUserId.equals(headerUserId)) {
      log.warn("User ID mismatch: token='{}' header='{}'", tokenUserId, headerUserId);
      unauthorized(response, "User ID mismatch between token and header");
      return;
    }

    if (sessionIntrospector != null && !sessionIntrospector.isActive(token)) {
      log.warn("Rejected request with revoked Keycloak session for userId={}", headerUserId);
      unauthorized(response, "Session has been revoked");
      return;
    }

    authenticate(request, headerUserId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void authenticate(HttpServletRequest request, String headerUserId) {
    List<SimpleGrantedAuthority> authorities =
        parseAuthorities(request.getHeader(HEADER_USER_ROLES));
    var principal =
        new KeycloakPrincipal(
            headerUserId,
            request.getHeader(HEADER_USERNAME),
            request.getHeader(HEADER_GEO_COUNTRY),
            request.getHeader(HEADER_GEO_CITY),
            request.getHeader(HEADER_GEO_LATITUDE),
            request.getHeader(HEADER_GEO_LONGITUDE),
            request.getHeader(HEADER_GEO_IP),
            request.getHeader(HttpHeaders.USER_AGENT),
            request.getHeader(HEADER_USER_SID));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    // No PII (username/ip/geo/sid) in logs; userId is an opaque identifier.
    log.debug("principal loaded userId={} roles={}", headerUserId, authorities.size());
  }

  private String bearerToken(String authorization) {
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      return null;
    }
    return authorization.substring(BEARER_PREFIX.length());
  }

  private void unauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"error\":\"" + message + "\"}");
  }

  private List<SimpleGrantedAuthority> parseAuthorities(String rolesHeader) {
    return extractRoleNames(rolesHeader).stream()
        .map(String::trim)
        .filter(role -> !role.isEmpty())
        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
        .toList();
  }

  /**
   * Roles arrive as a JSON array ({@code ["a","b"]}) from the gateway; CSV is a legacy fallback.
   */
  private List<String> extractRoleNames(String rolesHeader) {
    if (rolesHeader == null || rolesHeader.isBlank()) {
      return List.of();
    }
    String trimmed = rolesHeader.trim();
    if (trimmed.startsWith("[")) {
      try {
        return Arrays.asList(jsonMapper.readValue(trimmed, String[].class));
      } catch (Exception e) {
        log.warn("Failed to parse x-user-roles as JSON array: {}", e.getMessage());
      }
    }
    return Arrays.asList(trimmed.split(","));
  }

  private String extractUserIdFromToken(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length < 2) {
        return null;
      }
      byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
      JsonNode payload = jsonMapper.readTree(decoded);
      JsonNode sub = payload.get("sub");
      return sub != null ? sub.asText() : null;
    } catch (Exception e) {
      log.warn("Failed to decode JWT token: {}", e.getMessage());
      return null;
    }
  }
}
