package io.codehunters.jpa.auditing;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The auditor is the signed-in user's {@link Authentication#getName() name}: the JWT {@code sub}
 * for a resource server. Anonymous, unauthenticated or nameless callers give no auditor.
 */
public final class SecurityContextAuditorAware implements AuditorAware<String> {

  /** Creates the auditor. */
  public SecurityContextAuditorAware() {}

  @Override
  public Optional<String> getCurrentAuditor() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .filter(Authentication::isAuthenticated)
        .filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken))
        .map(Authentication::getName)
        .filter(name -> !name.isBlank());
  }
}
