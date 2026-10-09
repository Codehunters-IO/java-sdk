package io.codehunters.audit.internal.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Names the authenticated user of a Spring Security {@link Authentication}. Loaded only when Spring
 * Security is on the classpath.
 */
public final class SecurityActor {

  private static final Logger log = LoggerFactory.getLogger(SecurityActor.class);

  private SecurityActor() {}

  /**
   * Whether the authentication is a real user: authenticated and not anonymous.
   *
   * @param authentication the authentication
   * @return {@code true} for an authenticated, non-anonymous user
   */
  public static boolean isUser(Authentication authentication) {
    return authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken);
  }

  static String ofCallingThread() {
    try {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      return authentication != null && isUser(authentication) ? authentication.getName() : null;
    } catch (RuntimeException ex) {
      log.debug("The security context could not be read: {}", ex.getClass().getName());
      return null;
    }
  }
}
