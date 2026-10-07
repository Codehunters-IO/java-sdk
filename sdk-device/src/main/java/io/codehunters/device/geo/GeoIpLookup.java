package io.codehunters.device.geo;

import java.net.InetAddress;
import java.util.Optional;

/**
 * Looks up where an address is. Implementations should query a local database; sending client
 * addresses to a third-party HTTP service discloses personal data and is not supported.
 */
@FunctionalInterface
public interface GeoIpLookup {

  /**
   * Looks up an address.
   *
   * @param address the client address
   * @return the location, empty when the address is unknown, private or reserved
   */
  Optional<GeoLocation> lookup(InetAddress address);
}
