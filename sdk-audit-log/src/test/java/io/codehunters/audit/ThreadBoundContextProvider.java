package io.codehunters.audit;

import java.util.Optional;

/** Reads the actor from a thread-local, as providers backed by a security context do. */
final class ThreadBoundContextProvider implements AuditContextProvider {

  private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();

  static void actAs(String actor) {
    ACTOR.set(actor);
  }

  static void clear() {
    ACTOR.remove();
  }

  @Override
  public Optional<String> getActorId() {
    return Optional.ofNullable(ACTOR.get());
  }

  @Override
  public Optional<String> getActorType() {
    return Optional.empty();
  }

  @Override
  public Optional<String> getIpAddress() {
    return Optional.ofNullable(ACTOR.get()).map(actor -> "10.0.0.1");
  }

  @Override
  public Optional<String> getSourceService() {
    return Optional.empty();
  }
}
