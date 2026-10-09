---
status: accepted
date: 2026-10-07
decision-makers: [andres]
---

# 0012. Resolve the client IP only through trusted proxies

## Context and Problem Statement

Audit trails, rate limits and fraud checks need the client's IP address. Behind load balancers the
connection comes from a proxy, and the client address travels in `X-Forwarded-For` or `Forwarded`
(RFC 7239). Both are ordinary request headers: a client can send them, and proxies append to them
rather than replace them. The old `commons-util-ip-support` returned the leftmost
`X-Forwarded-For` value, which is exactly the part the client controls. How should the SDK decide
which address is the client?

## Decision Drivers

- A client must not be able to choose the address recorded for it.
- Safe without configuration; deployments behind proxies opt in explicitly.
- No DNS lookups or other I/O while handling a request.
- Usable outside Spring (the resolution is plain Java).

## Considered Options

- Option A — Trusted proxies: read the configured header right to left while the current address is
  a configured trusted proxy (CIDR); the first untrusted address is the client. No trusted proxies
  means the remote address.
- Option B — Leftmost `X-Forwarded-For` value, as before.
- Option C — Rely on the servlet container (`server.forward-headers-strategy`) only.

## Decision Outcome

Chosen option: **Option A**, because it is the only one where the recorded address cannot be chosen
by the client. Only one header is read, the one the trusted proxies write
(`codehunters.web.device.forwarding-header`, `X-Forwarded-For` by default): a proxy passes the other one
through untouched. A hop that is not an IP literal (`unknown`, an obfuscated identifier, garbage)
stops the walk, and only IP literals are parsed, so no host name is ever resolved.

### Consequences

- Good, because spoofing needs control of a trusted proxy, not just a header.
- Good, because the default (nothing trusted) is safe and needs no configuration.
- Bad, because every deployment behind proxies must list them; a missing entry yields the proxy's
  address instead of the client's.
- Neutral, because applications using `server.forward-headers-strategy=native` with
  `server.tomcat.remoteip.internal-proxies` listing their proxies can keep it and leave the trusted
  list empty; Option C is valid for them. `framework` (Spring's `ForwardedHeaderFilter`) takes the
  leftmost value without a trust check, so it must be turned off, or the address accepted as
  spoofable.
- Neutral, because client text must not be able to hide the hops proxies append to its line:
  `X-Forwarded-For` is split on every comma, and a `Forwarded` line with unbalanced quotes is too.

## Pros and Cons of the Options

### Option B — Leftmost `X-Forwarded-For`

- Good, because it needs no configuration.
- Bad, because any client sets its own recorded address.

### Option C — Container only

- Good, because it is built into Boot.
- Bad, because the trust rules live in container settings that differ between Tomcat, Jetty and
  `ForwardedHeaderFilter`, and plain-Java callers (messaging, batch) cannot reuse them.
- Bad, because `ForwardedHeaderFilter` (`framework`) has no trust check at all and returns the
  leftmost, client-controlled value.
