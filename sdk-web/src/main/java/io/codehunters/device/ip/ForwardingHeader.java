package io.codehunters.device.ip;

/**
 * The header the trusted proxies write the client address into. Only that header is read: a proxy
 * passes the other one through untouched, so whatever it holds came from the client.
 */
public enum ForwardingHeader {
  /**
   * {@code X-Forwarded-For}, written by nginx, HAProxy, cloud load balancers and most ingresses.
   */
  X_FORWARDED_FOR,
  /** {@code Forwarded} (RFC 7239), only when every trusted proxy writes it. */
  FORWARDED
}
