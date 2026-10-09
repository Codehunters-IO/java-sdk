package io.codehunters.security;

/**
 * Client context the API gateway forwards as {@code x-geo-*} and {@code User-Agent} headers.
 *
 * <p><strong>Client-supplied via gateway headers — not verified.</strong> The token proves who the
 * user is; nothing proves where these values came from. A caller that reaches the service without
 * going through the gateway can set every one of them. Use them for display or analytics, never for
 * authorization, rate limiting, fraud checks or audit trails. For a trusted client IP, use {@code
 * sdk-web}'s {@code ClientContextResolver}, which only honours forwarding headers from configured
 * trusted proxies.
 *
 * <p>{@link #toString()} omits every value, so the context cannot leak into logs by accident.
 *
 * @param country the country code the gateway reported, or {@code null}
 * @param city the city the gateway reported, or {@code null}
 * @param latitude the latitude the gateway reported, or {@code null}
 * @param longitude the longitude the gateway reported, or {@code null}
 * @param ip the client IP the gateway reported, or {@code null}
 * @param userAgent the client's {@code User-Agent}, or {@code null}
 */
public record GatewayContext(
    String country, String city, String latitude, String longitude, String ip, String userAgent) {

  @Override
  public String toString() {
    return "GatewayContext[redacted]";
  }
}
