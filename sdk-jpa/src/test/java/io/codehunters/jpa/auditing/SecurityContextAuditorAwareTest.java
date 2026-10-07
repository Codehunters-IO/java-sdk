package io.codehunters.jpa.auditing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextAuditorAwareTest {

  private final SecurityContextAuditorAware auditor = new SecurityContextAuditorAware();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_name_the_signed_in_user() {
    authenticate(new TestingAuthenticationToken("alice", "n/a", "ROLE_USER"));

    assertThat(auditor.getCurrentAuditor()).contains("alice");
  }

  @Test
  void should_be_empty_when_nobody_is_signed_in() {
    assertThat(auditor.getCurrentAuditor()).isEmpty();
  }

  @Test
  void should_be_empty_for_an_anonymous_caller() {
    authenticate(
        new AnonymousAuthenticationToken(
            "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

    assertThat(auditor.getCurrentAuditor()).isEmpty();
  }

  @Test
  void should_be_empty_for_a_caller_that_is_not_authenticated() {
    authenticate(UsernamePasswordAuthenticationToken.unauthenticated("mallory", "secret"));

    assertThat(auditor.getCurrentAuditor()).isEmpty();
  }

  @Test
  void should_be_empty_when_the_name_is_blank() {
    authenticate(new TestingAuthenticationToken(" ", "n/a", "ROLE_USER"));

    assertThat(auditor.getCurrentAuditor()).isEmpty();
  }

  private static void authenticate(Authentication authentication) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
