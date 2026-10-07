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

Hardened before release, after review:

- A client-sent quote can no longer swallow the hop a proxy appends to its line: `X-Forwarded-For`
  is split on every comma, and a `Forwarded` line with unbalanced quotes is too.
- IPv6 literals must start with a hex digit or `:`, so no input reaches the system resolver.
- Trusted entries reject ports and `/0` blocks, and accept IPv4-mapped CIDRs (`::ffff:10.0.0.0/104`).
- The connection's link-local remote address drops its zone id instead of being lost.
- `max-length x cache-size` is capped at 33554432 characters (64 MB).
- An `Error` while parsing releases waiting requests with `UNKNOWN` and is not cached.
- Any `UserAgentParser`, including the application's, gives `UNKNOWN` instead of failing the
  request when it throws or returns `null`.
- `ClientContextResolver.current()` ignores a request that has already completed.
- README and ADR 0012: only `server.forward-headers-strategy=native` with `internal-proxies`
  replaces `trusted-proxies`; `framework` trusts the leftmost value.
