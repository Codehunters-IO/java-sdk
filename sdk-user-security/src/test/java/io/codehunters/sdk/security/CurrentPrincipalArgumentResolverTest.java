package io.codehunters.sdk.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentPrincipalArgumentResolverTest {

  private final CurrentPrincipalArgumentResolver resolver = new CurrentPrincipalArgumentResolver();

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @SuppressWarnings("unused")
  void handler(@CurrentPrincipal KeycloakPrincipal principal, String other) {}

  private MethodParameter param(int index) throws NoSuchMethodException {
    Method method = getClass().getDeclaredMethod("handler", KeycloakPrincipal.class, String.class);
    return new MethodParameter(method, index);
  }

  @Test
  void supportsAnnotatedKeycloakPrincipalParameter() throws Exception {
    assertThat(resolver.supportsParameter(param(0))).isTrue();
  }

  @Test
  void doesNotSupportUnannotatedParameter() throws Exception {
    assertThat(resolver.supportsParameter(param(1))).isFalse();
  }

  @Test
  void resolvesPrincipalFromSecurityContext() throws Exception {
    KeycloakPrincipal principal =
        new KeycloakPrincipal(
            "u1", "user", "CO", "Bogota", "4.7110", "-74.0721", "1.2.3.4", "agent/1.0", "sess-1");
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));

    Object resolved = resolver.resolveArgument(param(0), null, null, null);

    assertThat(resolved).isEqualTo(principal);
  }

  @Test
  void resolvesNullWhenNoAuthentication() throws Exception {
    Object resolved = resolver.resolveArgument(param(0), null, null, null);

    assertThat(resolved).isNull();
  }
}
