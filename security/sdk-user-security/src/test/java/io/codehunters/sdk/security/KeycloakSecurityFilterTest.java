package io.codehunters.sdk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class KeycloakSecurityFilterTest {

  private static final String USER_ID = "user-123";
  private static final String USERNAME = "john.doe";
  private static final String ROLES = "ADMIN,USER";
  private static final String COUNTRY = "ES";
  private static final String CITY = "Barcelona";
  private static final String LATITUDE = "41.3851";
  private static final String LONGITUDE = "2.1734";
  private static final String IP = "192.168.1.1";
  private static final String SESSION_ID = "b1c2d3e4-session";

  private KeycloakSecurityFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private MockFilterChain filterChain;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    JsonMapper jsonMapper = JsonMapper.builder().build();
    filter = new KeycloakSecurityFilter(jsonMapper, List.of("/actuator/**", "/public/**"));
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    filterChain = new MockFilterChain();
    logAppender = new ListAppender<>();
    logAppender.start();
    ((Logger) LoggerFactory.getLogger(KeycloakSecurityFilter.class)).addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    Logger logger = (Logger) LoggerFactory.getLogger(KeycloakSecurityFilter.class);
    logger.detachAppender(logAppender);
    logger.setLevel(null);
  }

  @Test
  void should_setAuthentication_when_tokenMatchesIdentityHeaders()
      throws ServletException, IOException {
    request.addHeader("Authorization", "Bearer " + buildJwt(USER_ID));
    request.addHeader("x-user-id", USER_ID);
    request.addHeader("x-username", USERNAME);
    request.addHeader("x-user-roles", ROLES);

    filter.doFilter(request, response, filterChainCapturingAuth());

    assertEquals(200, response.getStatus());
  }

  @Test
  void should_rejectRequest_when_identityHeadersButNoToken() throws ServletException, IOException {
    // Core anti-spoofing guard: x-user-* headers alone must never authenticate a caller.
    request.addHeader("x-user-id", USER_ID);
    request.addHeader("x-user-roles", "[\"ADMIN\"]");

    filter.doFilter(request, response, filterChain);

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("Missing bearer token"));
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void should_rejectRequest_when_authorizationIsNotBearer() throws ServletException, IOException {
    request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
    request.addHeader("x-user-id", USER_ID);

    filter.doFilter(request, response, filterChain);

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("Missing bearer token"));
  }

  @Test
  void should_returnUnauthorized_when_tokenUserIdMismatch() throws ServletException, IOException {
    request.addHeader("Authorization", "Bearer " + buildJwt("different-user"));
    request.addHeader("x-user-id", USER_ID);

    filter.doFilter(request, response, filterChain);

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("User ID mismatch"));
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void should_returnUnauthorized_when_tokenHasNoSubClaim() throws ServletException, IOException {
    // Token whose payload lacks `sub` cannot be cross-checked against the header -> reject.
    String part = Base64.getUrlEncoder().withoutPadding().encodeToString("{}".getBytes());
    request.addHeader("Authorization", "Bearer " + part + "." + part + "." + part);
    request.addHeader("x-user-id", USER_ID);

    filter.doFilter(request, response, filterChain);

    assertEquals(401, response.getStatus());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void should_returnUnauthorized_when_tokenPayloadMalformed() throws ServletException, IOException {
    request.addHeader("Authorization", "Bearer header.NOT_BASE64!.signature");
    request.addHeader("x-user-id", USER_ID);

    filter.doFilter(request, response, filterChain);

    assertEquals(401, response.getStatus());
  }

  @Test
  void should_extractRoles_when_rolesHeaderPresent() throws ServletException, IOException {
    request.addHeader("Authorization", "Bearer " + buildJwt(USER_ID));
    request.addHeader("x-user-id", USER_ID);
    request.addHeader("x-user-roles", ROLES);

    var auth = captureAuthentication();

    assertNotNull(auth);
    List<String> authorities = auth.getAuthorities().stream().map(Object::toString).toList();
    assertTrue(authorities.contains("ROLE_ADMIN"));
    assertTrue(authorities.contains("ROLE_USER"));
  }

  @Test
  void should_extractRoles_when_rolesHeaderIsJsonArray() throws ServletException, IOException {
    request.addHeader("Authorization", "Bearer " + buildJwt(USER_ID));
    request.addHeader("x-user-id", USER_ID);
    request.addHeader("x-user-roles", "[\"vitxo-manager\",\"uma_protection\"]");

    var auth = captureAuthentication();

    assertNotNull(auth);
    List<String> authorities = auth.getAuthorities().stream().map(Object::toString).toList();
    assertTrue(authorities.contains("ROLE_VITXO-MANAGER"));
    assertTrue(authorities.contains("ROLE_UMA_PROTECTION"));
  }

  @Test
  void should_passThrough_when_noUserIdHeader() throws ServletException, IOException {
    var auth = captureAuthentication();

    assertNull(auth);
    assertEquals(200, response.getStatus());
  }

  @Test
  void should_clearSecurityContext_afterFilter() throws ServletException, IOException {
    request.addHeader("Authorization", "Bearer " + buildJwt(USER_ID));
    request.addHeader("x-user-id", USER_ID);
    request.addHeader("x-username", USERNAME);

    filter.doFilter(request, response, filterChain);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void should_skipFilter_when_pathExcluded() throws ServletException, IOException {
    request.setRequestURI("/actuator/health");

    assertTrue(filter.shouldNotFilter(request));
  }

  @Test
  void should_setGeolocationData_when_geoHeadersPresent() throws ServletException, IOException {
    request.addHeader("Authorization", "Bearer " + buildJwt(USER_ID));
    request.addHeader("x-user-id", USER_ID);
    request.addHeader("x-username", USERNAME);
    request.addHeader("x-geo-country", COUNTRY);
    request.addHeader("x-geo-city", CITY);
    request.addHeader("x-geo-latitude", LATITUDE);
    request.addHeader("x-geo-longitude", LONGITUDE);
    request.addHeader("x-geo-ip", IP);
    request.addHeader("User-Agent", "Mozilla/5.0 test-agent");

    KeycloakPrincipal principal = capturePrincipal();

    assertNotNull(principal);
    assertEquals(USER_ID, principal.userId());
    assertEquals(USERNAME, principal.username());
    assertEquals(COUNTRY, principal.country());
    assertEquals(CITY, principal.city());
    assertEquals(LATITUDE, principal.latitude());
    assertEquals(LONGITUDE, principal.longitude());
    assertEquals(IP, principal.ip());
    assertEquals("Mozilla/5.0 test-agent", principal.userAgent());
  }

  @Test
  void should_setSessionId_when_sidHeaderPresent() throws ServletException, IOException {
    request.addHeader("Authorization", "Bearer " + buildJwt(USER_ID));
    request.addHeader("x-user-id", USER_ID);
    request.addHeader("x-user-sid", SESSION_ID);

    KeycloakPrincipal principal = capturePrincipal();

    assertNotNull(principal);
    assertEquals(SESSION_ID, principal.sessionId());
  }

  @Test
  void should_leaveSessionIdNull_when_sidHeaderAbsent() throws ServletException, IOException {
    request.addHeader("Authorization", "Bearer " + buildJwt(USER_ID));
    request.addHeader("x-user-id", USER_ID);

    KeycloakPrincipal principal = capturePrincipal();

    assertNotNull(principal);
    assertNull(principal.sessionId());
  }

  @Test
  void should_skipAuthentication_when_userIdHeaderAbsent() throws ServletException, IOException {
    request.addHeader("Authorization", "Bearer " + buildJwt(USER_ID));

    filter.doFilter(request, response, filterChain);

    assertEquals(200, response.getStatus());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void should_logPrincipalWithoutPii_atDebugLevel() throws ServletException, IOException {
    ((Logger) LoggerFactory.getLogger(KeycloakSecurityFilter.class)).setLevel(Level.DEBUG);
    request.addHeader("Authorization", "Bearer " + buildJwt(USER_ID));
    request.addHeader("x-user-id", USER_ID);
    request.addHeader("x-username", USERNAME);
    request.addHeader("x-user-roles", ROLES);
    request.addHeader("x-geo-ip", IP);
    request.addHeader("x-user-sid", SESSION_ID);

    filter.doFilter(request, response, filterChain);

    assertThat(logAppender.list)
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
              assertThat(event.getFormattedMessage())
                  .contains("principal loaded")
                  .contains("userId=" + USER_ID)
                  .doesNotContain(USERNAME)
                  .doesNotContain(IP)
                  .doesNotContain(SESSION_ID);
            });
  }

  @Test
  void should_rejectRequest_when_sessionRevoked() throws ServletException, IOException {
    filter =
        new KeycloakSecurityFilter(
            JsonMapper.builder().build(), List.of(), stubIntrospector(false));
    request.addHeader("Authorization", "Bearer " + buildJwt(USER_ID));
    request.addHeader("x-user-id", USER_ID);

    filter.doFilter(request, response, filterChain);

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("Session has been revoked"));
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void should_allowRequest_when_sessionActive() throws ServletException, IOException {
    filter =
        new KeycloakSecurityFilter(JsonMapper.builder().build(), List.of(), stubIntrospector(true));
    request.addHeader("Authorization", "Bearer " + buildJwt(USER_ID));
    request.addHeader("x-user-id", USER_ID);

    var auth = captureAuthentication();

    assertEquals(200, response.getStatus());
    assertNotNull(auth);
  }

  private org.springframework.security.core.Authentication captureAuthentication()
      throws ServletException, IOException {
    var holder =
        new Object() {
          org.springframework.security.core.Authentication auth;
        };
    filter.doFilter(
        request,
        response,
        (req, res) -> holder.auth = SecurityContextHolder.getContext().getAuthentication());
    return holder.auth;
  }

  private KeycloakPrincipal capturePrincipal() throws ServletException, IOException {
    var auth = captureAuthentication();
    return auth == null ? null : (KeycloakPrincipal) auth.getPrincipal();
  }

  private SessionIntrospector stubIntrospector(boolean active) {
    var cfg = new KeycloakSecurityProperties();
    cfg.setUrl("http://kc");
    cfg.setRealm("vitxo");
    return new SessionIntrospector(RestClient.create(), JsonMapper.builder().build(), cfg) {
      @Override
      public boolean isActive(String token) {
        return active;
      }
    };
  }

  private FilterChain filterChainCapturingAuth() {
    return (req, res) -> {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      assertNotNull(auth);
      assertTrue(auth.getPrincipal() instanceof KeycloakPrincipal);
    };
  }

  private String buildJwt(String subject) {
    String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{}".getBytes());
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(("{\"sub\":\"" + subject + "\"}").getBytes());
    String signature = Base64.getUrlEncoder().withoutPadding().encodeToString("sig".getBytes());
    return header + "." + payload + "." + signature;
  }
}
