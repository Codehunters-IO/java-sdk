# Migrating from commons-util-ip-support and commons-util-device-support

`io.codehunters.commons:commons-util-ip-support` and `commons-util-device-support` 5.1.0
(`codehuntersio-java-commons-support`) are replaced by `io.codehunters:sdk-device` 1.0.0. Every class
was rewritten; none is source compatible.

| commons-support | sdk-device |
|---|---|
| `IpUtil.extractIp(request)` | `ClientContextResolver.resolve(request).ipAddress()`, or `ClientIpResolver.resolve(ProxyChain)` |
| `IpUtil.extractLocation(ip)`, `IpApiLocationHttpClient`, `IpLocation` | `GeoIpLookup` port, no implementation (see below) |
| `DeviceUtil.device(request[, useLocation])`, `Device` | `ClientContext` (IP, parsed and raw `User-Agent`); location through your own `GeoIpLookup` |
| `DeviceUtil.getUserAgent(request)` (`"family.major.minor\|os…\|device"` string) | `ClientContext.userAgent()` (`UserAgentInfo` record) |
| `DeviceUtil.getClient(request)` (`ua_parser.Client`, `null` on failure) | `UserAgentParser.parse(header)`, never null |
| `uap-java` 1.4.0, always on the classpath, a new `Parser` per call | Optional `uap-java` 1.6.1, one parser, bounded cache, header capped before parsing |

## Intentionally not migrated

- **The leftmost `X-Forwarded-For` entry as the client IP.** `IpUtil` returned the first value of the
  header, which the client controls: any caller could set its own address, in audit trails and rate
  limits alike. `sdk-device` believes the header only as far as configured trusted proxies wrote it,
  and uses the remote address when none are configured.
- **The ip-api.com lookup.** `IpApiLocationHttpClient` sent every client address to a third party,
  over plain HTTP, on the request thread and without a timeout. Geolocation is a port now; back it
  with a local database.
- **`Device.serialId`, `additionalInfo` and the mutable builder.** `ClientContext` is an immutable
  record; data an application adds belongs in its own types.
