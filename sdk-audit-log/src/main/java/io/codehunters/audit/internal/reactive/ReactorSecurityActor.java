package io.codehunters.audit.internal.reactive;

import io.codehunters.audit.internal.security.SecurityActor;
import java.util.Optional;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

/**
 * Reads the authenticated user from the subscriber's Reactor context, where WebFlux keeps it.
 * Loaded only when Reactor and Spring Security are on the classpath.
 */
final class ReactorSecurityActor {

  private ReactorSecurityActor() {}

  /**
   * The name of the subscriber's authenticated, non-anonymous user.
   *
   * @return the name, or empty when the context holds none or cannot be read
   */
  static Mono<Optional<String>> current() {
    return ReactiveSecurityContextHolder.getContext()
        .mapNotNull(SecurityContext::getAuthentication)
        .filter(SecurityActor::isUser)
        .map(authentication -> Optional.of(authentication.getName()))
        .onErrorResume(ex -> Mono.empty())
        .defaultIfEmpty(Optional.empty());
  }
}
