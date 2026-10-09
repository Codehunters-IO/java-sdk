package io.codehunters.audit.internal.security;

import org.springframework.util.ClassUtils;

/**
 * The authenticated user of the calling thread, as a servlet application keeps it in Spring
 * Security's {@code SecurityContextHolder}. Spring Security is optional: without it there is no
 * actor, and none of its types is loaded.
 */
public final class CallingThreadActor {

  private static final boolean SECURITY_PRESENT =
      ClassUtils.isPresent(
          "org.springframework.security.core.context.SecurityContextHolder",
          CallingThreadActor.class.getClassLoader());

  private CallingThreadActor() {}

  /**
   * The name of the calling thread's authenticated, non-anonymous user.
   *
   * @return the name, or {@code null} when there is none, it cannot be read, or Spring Security is
   *     absent
   */
  public static String current() {
    return SECURITY_PRESENT ? SecurityActor.ofCallingThread() : null;
  }
}
