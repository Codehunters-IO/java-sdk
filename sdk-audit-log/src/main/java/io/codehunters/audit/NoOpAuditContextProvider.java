package io.codehunters.audit;

import java.util.Optional;

/**
 * Default no-op implementation. Returns empty values for all context fields.
 *
 * @since 1.0.0
 */
public final class NoOpAuditContextProvider implements AuditContextProvider {

  /** Creates the no-op provider. */
  public NoOpAuditContextProvider() {}

  @Override
  public Optional<String> getActorId() {
    return Optional.empty();
  }

  @Override
  public Optional<String> getActorType() {
    return Optional.empty();
  }

  @Override
  public Optional<String> getIpAddress() {
    return Optional.empty();
  }

  @Override
  public Optional<String> getSourceService() {
    return Optional.empty();
  }
}
