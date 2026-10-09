package io.codehunters.security.gateway;

import io.codehunters.security.Authorities;
import io.codehunters.security.KeycloakAuthenticationToken;
import io.codehunters.security.KeycloakPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reconciles the {@code x-user-*} headers stamped by the API gateway with the verified token. The
 * token is the only source of identity and authorities: a header that names another user or grants
 * a role the token lacks rejects the request with 401. The gateway's geolocation and client headers
 * are then added to the principal as untrusted context.
 *
 * <p>Runs after bearer-token authentication. Identity headers on a request without a token are
 * rejected, since only a caller that bypassed the gateway would send them.
 *
 * <p>A rejection goes through the configured {@link AuthenticationEntryPoint}, so it answers like
 * every other 401 of the application (with {@code sdk-web}, a problem detail with {@code code} and
 * {@code traceId}). Without one, the filter writes a problem detail of the same shape itself.
 */
public class GatewayHeadersFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(GatewayHeadersFilter.class);
  private static final String USER_ID = "x-user-id";
  private static final String USER_ROLES = "x-user-roles";
  private static final String GEO_COUNTRY = "x-geo-country";
  private static final String GEO_CITY = "x-geo-city";
  private static final String GEO_LATITUDE = "x-geo-latitude";
  private static final String GEO_LONGITUDE = "x-geo-longitude";
  private static final String GEO_IP = "x-geo-ip";

  private static final String UNAUTHORIZED_CODE = "UNAUTHORIZED";

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final AuthenticationEntryPoint entryPoint;

  /** Creates the filter, answering rejections with its own problem detail. */
  public GatewayHeadersFilter() {
    this(null);
  }

  /**
   * Creates the filter, answering rejections through an entry point.
   *
   * @param entryPoint answers rejected requests; {@code null} writes the filter's own problem
   *     detail
   */
  public GatewayHeadersFilter(AuthenticationEntryPoint entryPoint) {
    this.entryPoint = entryPoint;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication =
        SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (!(principal instanceof KeycloakPrincipal tokenPrincipal)) {
      continueAnonymous(request, response, chain);
      return;
    }
    Optional<String> mismatch = mismatch(request, tokenPrincipal, authentication);
    if (mismatch.isPresent()) {
      reject(request, response, mismatch.get());
      return;
    }
    SecurityContextHolder.getContextHolderStrategy()
        .setContext(withGatewayContext(tokenPrincipal, authentication, request));
    chain.doFilter(request, response);
  }

  private void continueAnonymous(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getHeader(USER_ID) != null) {
      reject(request, response, "Identity headers require a bearer token");
      return;
    }
    chain.doFilter(request, response);
  }

  private Optional<String> mismatch(
      HttpServletRequest request, KeycloakPrincipal principal, Authentication authentication) {
    String headerUserId = request.getHeader(USER_ID);
    if (headerUserId != null && !headerUserId.equals(principal.userId())) {
      return Optional.of("x-user-id does not match the token subject");
    }
    return rolesMismatch(request.getHeader(USER_ROLES), authentication);
  }

  private Optional<String> rolesMismatch(String header, Authentication authentication) {
    if (header == null || header.isBlank()) {
      return Optional.empty();
    }
    Optional<List<String>> roles = parseRoles(header.trim());
    if (roles.isEmpty()) {
      return Optional.of("x-user-roles is malformed");
    }
    Set<String> granted =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
    boolean escalates =
        roles.get().stream().map(Authorities::fromRole).anyMatch(role -> !granted.contains(role));
    return escalates
        ? Optional.of("x-user-roles grants roles the token does not")
        : Optional.empty();
  }

  /** Roles arrive as a JSON array from the gateway; comma-separated values are still accepted. */
  private Optional<List<String>> parseRoles(String header) {
    if (!header.startsWith("[")) {
      return Optional.of(
          Arrays.stream(header.split(",")).map(String::trim).filter(r -> !r.isEmpty()).toList());
    }
    try {
      List<String> roles = Arrays.asList(jsonMapper.readValue(header, String[].class));
      return roles.contains(null) ? Optional.empty() : Optional.of(roles);
    } catch (JacksonException e) {
      return Optional.empty();
    }
  }

  private static SecurityContext withGatewayContext(
      KeycloakPrincipal principal, Authentication authentication, HttpServletRequest request) {
    var enriched =
        new KeycloakPrincipal(
            principal.userId(),
            principal.username(),
            request.getHeader(GEO_COUNTRY),
            request.getHeader(GEO_CITY),
            request.getHeader(GEO_LATITUDE),
            request.getHeader(GEO_LONGITUDE),
            request.getHeader(GEO_IP),
            request.getHeader(HttpHeaders.USER_AGENT),
            principal.sessionId());
    SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
    context.setAuthentication(rewrap(authentication, enriched));
    return context;
  }

  // The validated token stays with the authentication, so later filters never read it again from
  // the request.
  private static Authentication rewrap(Authentication authentication, KeycloakPrincipal enriched) {
    if (authentication instanceof KeycloakAuthenticationToken keycloak) {
      return keycloak.withPrincipal(enriched);
    }
    var token =
        UsernamePasswordAuthenticationToken.authenticated(
            enriched, authentication.getCredentials(), authentication.getAuthorities());
    token.setDetails(authentication.getDetails());
    return token;
  }

  private void reject(HttpServletRequest request, HttpServletResponse response, String detail)
      throws ServletException, IOException {
    log.warn("Rejected request: {}", detail);
    if (entryPoint != null) {
      entryPoint.commence(request, response, new BadCredentialsException(detail));
      return;
    }
    writeProblem(request, response, detail);
  }

  private void writeProblem(HttpServletRequest request, HttpServletResponse response, String detail)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(), problem(request, detail));
  }

  /** The members {@code sdk-web} writes for a 401, minus the {@code traceId} only it resolves. */
  private static Map<String, Object> problem(HttpServletRequest request, String detail) {
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("type", "about:blank");
    problem.put("title", HttpStatus.UNAUTHORIZED.getReasonPhrase());
    problem.put("status", HttpStatus.UNAUTHORIZED.value());
    problem.put("detail", detail);
    problem.put("instance", request.getRequestURI());
    problem.put("code", UNAUTHORIZED_CODE);
    return problem;
  }
}
