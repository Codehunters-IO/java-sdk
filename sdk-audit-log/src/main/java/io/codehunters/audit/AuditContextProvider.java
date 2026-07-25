package io.codehunters.audit;

import java.util.Optional;

/**
 * Provides cross-cutting audit context (actor, IP, HTTP) so it doesn't need to be repeated on every
 * annotation. Implementations typically read from SecurityContext, MDC, or HttpServletRequest.
 *
 * @since 1.0.0
 */
public interface AuditContextProvider {

  /**
   * Current actor identifier.
   *
   * @return the actor ID, if available
   */
  Optional<String> getActorId();

  /**
   * Current actor type.
   *
   * @return the actor type, if available
   */
  Optional<String> getActorType();

  /**
   * Caller IP address.
   *
   * @return the IP address, if available
   */
  Optional<String> getIpAddress();

  /**
   * Originating service name.
   *
   * @return the source service, if available
   */
  Optional<String> getSourceService();

  /**
   * Caller user agent.
   *
   * @return the user agent, if available; empty by default
   */
  default Optional<String> getUserAgent() {
    return Optional.empty();
  }

  /**
   * HTTP method of the current request.
   *
   * @return the HTTP method, if available; empty by default
   */
  default Optional<String> getHttpMethod() {
    return Optional.empty();
  }

  /**
   * Requested resource path.
   *
   * @return the resource path, if available; empty by default
   */
  default Optional<String> getResource() {
    return Optional.empty();
  }
}
