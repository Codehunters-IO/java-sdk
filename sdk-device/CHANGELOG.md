# Changelog — sdk-device

## 1.0.0

First release ([ADR 0012](../docs/adr/0012-trusted-proxy-client-ip.md)).

- `ClientIpResolver`: client address behind trusted proxies (`TrustedProxies`, CIDR, IPv4 and
  IPv6), reading only the configured `ForwardingHeader` (`X-Forwarded-For` by default, or RFC 7239
  `Forwarded`) right to left. No trusted proxies means the remote address. Unparseable hops stop the
  walk. Literals are parsed without DNS (`IpLiterals`).
- `UserAgentParser` port with `UapUserAgentParser` (optional `uap-java`), `UnknownUserAgentParser`
  and `CachingUserAgentParser` (bounded LRU, one parse per header in flight). `UserAgents.sanitize`
  replaces control characters and caps the length before parsing.
- `ClientContext` and, for servlet requests, `ClientContextResolver` (once per request; `current()`
  for the request bound to the thread).
- `GeoIpLookup` port and `GeoLocation`, without an implementation.
- `codehunters.device.*` properties, validated at startup.
- `ip`, `useragent` and `geo` depend on the JDK only; an ArchUnit test enforces it.
- Replaces `commons-util-ip-support` and `commons-util-device-support` from
  `codehuntersio-java-commons-support`; see
  [MIGRATION-from-commons-support.md](MIGRATION-from-commons-support.md).
