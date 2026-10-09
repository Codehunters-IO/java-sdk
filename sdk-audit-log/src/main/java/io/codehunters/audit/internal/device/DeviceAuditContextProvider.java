package io.codehunters.audit.internal.device;

import io.codehunters.audit.AuditContextProvider;
import io.codehunters.web.device.ClientContext;
import io.codehunters.web.device.servlet.ClientContextResolver;
import java.util.Optional;

/**
 * Default {@link AuditContextProvider} when the device SDK is on the classpath: the IP and {@code
 * User-Agent} of the current servlet request, resolved behind its trusted proxies. Actor and source
 * service stay empty; applications that know them define their own provider.
 */
public final class DeviceAuditContextProvider implements AuditContextProvider {

  private final ClientContextResolver resolver;

  public DeviceAuditContextProvider(ClientContextResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public Optional<String> getActorId() {
    return Optional.empty();
  }

  @Override
  public Optional<String> getActorType() {
    return Optional.empty();
  }

  @Override
  public Optional<String> getSourceService() {
    return Optional.empty();
  }

  @Override
  public Optional<String> getIpAddress() {
    return resolver.current().flatMap(ClientContext::ipAddress);
  }

  @Override
  public Optional<String> getUserAgent() {
    return resolver.current().map(ClientContext::rawUserAgent).filter(agent -> !agent.isEmpty());
  }
}
