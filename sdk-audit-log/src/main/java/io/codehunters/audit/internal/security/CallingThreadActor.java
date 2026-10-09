package io.codehunters.audit.internal.security;

import org.springframework.util.ClassUtils;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * The authenticated user of the calling thread, as a servlet application keeps it in Spring
 * Security's {@code SecurityContextHolder}, read only while a request is bound to the thread. A
 * pooled thread can inherit a context (with {@code MODE_INHERITABLETHREADLOCAL}) or keep one that
 * leaked; outside a request it would name a plausible but wrong user, so there is no actor there.
 * Spring Security and Spring Web are optional: without either there is no actor, and none of their
 * types is loaded.
 */
public final class CallingThreadActor {

  private static final ClassLoader LOADER = CallingThreadActor.class.getClassLoader();
  private static final boolean SECURITY_PRESENT =
      ClassUtils.isPresent(
          "org.springframework.security.core.context.SecurityContextHolder", LOADER);
  private static final boolean WEB_PRESENT =
      ClassUtils.isPresent("org.springframework.web.context.request.RequestContextHolder", LOADER);

  private CallingThreadActor() {}

  /**
   * The name of the calling thread's authenticated, non-anonymous user, while a request is bound.
   *
   * @return the name, or {@code null} when no request is bound, there is no user, it cannot be
   *     read, or Spring Security or Spring Web is absent
   */
  public static String current() {
    return SECURITY_PRESENT && WEB_PRESENT && RequestBinding.isBound()
        ? SecurityActor.ofCallingThread()
        : null;
  }

  /** Reads Spring Web's request binding; loaded only when Spring Web is present. */
  private static final class RequestBinding {

    private RequestBinding() {}

    static boolean isBound() {
      return RequestContextHolder.getRequestAttributes() != null;
    }
  }
}
