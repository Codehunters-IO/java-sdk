package io.codehunters.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;

class KeycloakPrincipalTest {

  private static final String USER_ID = "user-1";
  private static final String USERNAME = "alice";
  private static final String COUNTRY = "CO";
  private static final String CITY = "Bogota";
  private static final String LATITUDE = "4.7110";
  private static final String LONGITUDE = "-74.0721";
  private static final String IP = "203.0.113.7";
  private static final String USER_AGENT = "agent/1.0";
  private static final String SESSION_ID = "sid-1";

  private final KeycloakPrincipal principal =
      new KeycloakPrincipal(
          USER_ID, USERNAME, COUNTRY, CITY, LATITUDE, LONGITUDE, IP, USER_AGENT, SESSION_ID);

  @Test
  void should_be_an_authenticated_principal_named_after_the_user_id() {
    assertThat(principal).isInstanceOf(AuthenticatedPrincipal.class);
    assertThat(principal.getName()).isEqualTo(USER_ID);
  }

  @Test
  void should_name_the_authentication_after_the_user_id_when_used_as_principal() {
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());

    assertThat(authentication.getName()).isEqualTo(USER_ID);
  }

  @Test
  void should_keep_gateway_context_and_personal_data_out_of_to_string() {
    assertThat(principal.toString())
        .contains(USER_ID)
        .doesNotContain(USERNAME, COUNTRY, CITY, LATITUDE, LONGITUDE, IP, USER_AGENT, SESSION_ID);
  }

  @Test
  void should_expose_gateway_headers_as_gateway_context() {
    assertThat(principal.gatewayContext())
        .isEqualTo(new GatewayContext(COUNTRY, CITY, LATITUDE, LONGITUDE, IP, USER_AGENT));
  }

  @Test
  void should_keep_gateway_values_out_of_gateway_context_to_string() {
    assertThat(principal.gatewayContext().toString())
        .doesNotContain(COUNTRY, CITY, LATITUDE, LONGITUDE, IP, USER_AGENT);
  }
}
