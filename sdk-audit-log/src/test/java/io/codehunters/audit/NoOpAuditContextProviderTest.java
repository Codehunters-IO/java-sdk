package io.codehunters.audit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoOpAuditContextProviderTest {

  private final NoOpAuditContextProvider provider = new NoOpAuditContextProvider();

  @Test
  void should_return_empty_for_all_context_fields() {
    assertTrue(provider.getActorId().isEmpty());
    assertTrue(provider.getActorType().isEmpty());
    assertTrue(provider.getIpAddress().isEmpty());
    assertTrue(provider.getSourceService().isEmpty());
    assertTrue(provider.getUserAgent().isEmpty());
    assertTrue(provider.getHttpMethod().isEmpty());
    assertTrue(provider.getResource().isEmpty());
  }
}
