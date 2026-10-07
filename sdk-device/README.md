# sdk-device

Who is calling a service: the client IP as resolved behind **trusted** proxies, the parsed
`User-Agent`, and a port for geo-IP lookups that keeps addresses inside the service.

```kotlin
implementation(platform("io.codehunters:sdk-bom:<version>"))
implementation("io.codehunters:sdk-device")
// Optional: real User-Agent parsing. Without it every client is "Other".
implementation("com.github.ua-parser:uap-java:1.6.1")
```

## Client IP

`X-Forwarded-For` and `Forwarded` are headers the client can send too. The SDK only believes them as
far as your own proxies wrote them ([ADR 0012](../docs/adr/0012-trusted-proxy-client-ip.md)):

```yaml
codehunters:
  device:
    trusted-proxies: 10.0.0.0/8, 2001:db8::/32   # your load balancers / ingress
    forwarding-header: x-forwarded-for            # or "forwarded" (RFC 7239)
```

- **No trusted proxies (the default):** the connection's remote address is the client. Forwarding
  headers are ignored, so nothing can be spoofed.
- **Trusted proxies:** starting from the remote address, hops are read right to left while the
  current address is a trusted proxy; the first address that is not trusted is the client. If every
  hop is trusted, the leftmost one is returned.
- **Only the configured header is read.** Proxies that append `X-Forwarded-For` pass a client-sent
  `Forwarded` through untouched (and vice versa), so reading both would let the client choose.
- **Unparseable hops stop the walk.** `unknown`, obfuscated identifiers (`_hidden`) and garbage are
  never returned; the last address with a known origin is, because anything to its left may have
  been written by the client.
- **Client text cannot swallow proxy hops.** Proxies append to the line the client sent (`"`,
  then `, 203.0.113.9`). `X-Forwarded-For` has no quoted strings and is split on every comma; a
  `Forwarded` line whose quotes do not balance is split on every comma too, so only the client's own
  elements become invalid.
- **No DNS.** Only IP literals are parsed (IPv4, IPv6 with or without brackets, optional port); an
  IPv6 literal must start with a hex digit or `:`. Host names and zone ids in headers are rejected.
  The connection's own link-local address keeps its address and drops its zone id
  (`fe80::1%en0` becomes `fe80:0:0:0:0:0:0:1`).
- **Trusted entries are strict:** no ports, and no `0.0.0.0/0` or `::/0` (trusting every hop
  would return the client-written leftmost one). IPv4-mapped blocks are accepted
  (`::ffff:10.0.0.0/104` is `10.0.0.0/8`).
- IPv6 addresses are returned in the JDK's full form (`2001:db8:0:0:0:0:0:1`).

### Container forward headers

`server.forward-headers-strategy` rewrites the remote address before the SDK sees it:

- **`native`** (Tomcat's `RemoteIpValve`) is equivalent to `trusted-proxies` only when
  `server.tomcat.remoteip.internal-proxies` lists your proxies. Then leave `trusted-proxies` empty:
  the remote address is already the resolved client.
- **`framework`** (Spring's `ForwardedHeaderFilter`) takes the **leftmost** `Forwarded` or
  `X-Forwarded-For` value without any trust check, so any client can choose its address. Turn it
  off and configure `trusted-proxies` instead, or accept that the address can be spoofed.

The resolution itself is plain Java (`io.codehunters.device.ip`), usable outside Spring:

```java
var resolver = new ClientIpResolver(
    TrustedProxies.of(List.of("10.0.0.0/8")), ForwardingHeader.X_FORWARDED_FOR);
Optional<InetAddress> client =
    resolver.resolve(new ProxyChain(remoteAddr, forwardedLines, xForwardedForLines));
```

## User-Agent

- The header is sanitized first: control characters become spaces and it is cut to
  `user-agent.max-length` (512) characters, so a crafted header cannot make the regex-based parser
  slow and never ends in half a surrogate pair.
- With `uap-java` on the classpath, `UapUserAgentParser` reads browser, OS and device families and
  versions; parses are kept in a bounded LRU cache (`user-agent.cache-size`, 1024; 0 disables it),
  and concurrent requests with the same header share one parse. `max-length x cache-size` may not
  exceed 33554432 characters (64 MB). The cache saves repeated parses only: clients sending a
  different header each time still cost one parse of up to `max-length` characters per request.
- Without it, `UnknownUserAgentParser` answers `UserAgentInfo.UNKNOWN` (families `Other`, empty
  versions). The sanitized raw header is still available.
- A parser exception, or a `null` answer from any parser including your own, gives `UNKNOWN`; it
  never fails the request. An `Error` thrown while parsing (a `StackOverflowError` in the regex
  engine, say) releases the requests waiting on that parse with `UNKNOWN` and is not cached.
- Define your own `UserAgentParser` bean to use another library.

`uap-java` 1.6.1 (December 2023) is the latest release and has no published advisories; the ReDoS
issues reported against `ua-parser` regexes were fixed in the bundled `uap-core` before it. The
length cap is the defence in depth.

## Request context

In servlet applications `ClientContextResolver` combines both, once per request (kept as a request
attribute):

```java
ClientContext client = clientContextResolver.resolve(request);   // or .current()
client.ipAddress();      // Optional<String>
client.userAgent();      // UserAgentInfo, never null
client.rawUserAgent();   // sanitized, truncated; "" when absent
```

`sdk-audit-log` uses it when both are present: its default `AuditContextProvider` then fills the
IP and `User-Agent` of audit events.

## Geo-IP

`GeoIpLookup` is a port only; the SDK ships no implementation. Back it with a local database such as
MaxMind GeoLite2/GeoIP2. Sending client addresses to a third-party HTTP API (as the old
`commons-util-ip-support` did with ip-api.com, over plain HTTP) discloses personal data and is not
supported.

## Configuration

| Property | Default | Notes |
|---|---|---|
| `codehunters.device.enabled` | `true` | |
| `codehunters.device.trusted-proxies` | empty | Addresses or CIDR blocks; an invalid entry, a port or a `/0` block fails startup |
| `codehunters.device.forwarding-header` | `x-forwarded-for` | Or `forwarded` |
| `codehunters.device.user-agent.max-length` | `512` | 16 to 8192 |
| `codehunters.device.user-agent.cache-size` | `1024` | 0 to 100000; 0 disables the cache; `max-length x cache-size` at most 33554432 |

Every bean (`ClientIpResolver`, `UserAgentParser`, `ClientContextResolver`) backs off when the
application defines its own. `ClientContextResolver` is registered in servlet applications only.

## Known limits

- WebFlux is not supported: there is no reactive `ClientContextResolver`. The plain-Java
  `ClientIpResolver` can be called with a `ProxyChain` built from a `ServerHttpRequest`.
- `RFC 7239` `by`, `host` and `proto` parameters are not used.
