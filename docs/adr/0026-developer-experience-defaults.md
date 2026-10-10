# 0026 — Developer experience defaults: opt-in side effects, platform caches, a faster outbox

- Status: accepted
- Date: 2026-10-10

## Context

The architecture review of the SDKs found that adding a jar could change an application's behaviour
without asking (JPA auditing for the whole application, session introspection on every new token,
device beans in non-web applications, a global `@EnableScheduling` and a global `Clock` from the
outbox). In-process caches were hand-written LRU maps behind one lock, without metrics. The outbox
write API took seven positional strings on mutable objects, and dispatch only happened on a polling
schedule (a few messages per second, one message per key every 11 to 15 seconds). Audit evaluated
SpEL with full method and type access. file-share carried its own copy of Spring Security's OAuth2
client interceptor.

## Decision

- **Side effects are opt-in.** Anything that changes behaviour outside the SDK's own beans needs a
  property:
  - `codehunters.jpa.auditing.enabled` (default `false`);
  - `codehunters.security.introspection.enabled` (default `false`). This is a security-relevant
    default change: revoked sessions are only refused before the token expires when it is enabled;
  - sdk-web device beans register in servlet applications only;
  - the outbox runs its tasks on a private `outboxTaskScheduler` with a private `outboxClock`; the
    SDK no longer enables scheduling or exposes a `Clock` for the application.
- **In-process caches use Caffeine** (version managed by the Spring Boot platform) with Micrometer
  cache metrics when a `MeterRegistry` exists: `codehunters.parameter.cache` (with invalidation),
  `codehunters.jasper.template.cache`, `codehunters.web.device.user-agent.cache` and
  `codehunters.security.introspection.cache`. Loads stay single-flight and failures are not cached.
- **The outbox is written through an immutable model and a fluent API**: records with `Instant`,
  `OutboxEvent.to(topic).key(k).type(t).payload(p)`, and two ports, `OutboxWriter` for applications
  and `OutboxDispatchStore` for the dispatcher. Dispatch also starts after the writing transaction
  commits (coalesced), claims up to `claim.max-per-key` consecutive rows per key in order, and can
  requeue `FAILED` rows. Timing properties are `Duration`s. The outbox JSON mapper is a copy of
  Boot's builder.
- **Audit expressions run in a read-only SpEL sandbox** (properties, indexes and operators only);
  expressions the sandbox refuses are reported at startup.
- **file-share uses Spring Security's `OAuth2ClientHttpRequestInterceptor`** and adds a signed
  upload helper and an in-memory test fake.

## Consequences

- Applications that relied on the previous defaults must opt in (`MIGRATION` guides of jpa 3.0,
  user-security 5.0, web 3.0, messaging 9.0, audit 5.0).
- Outbox commit-to-publish latency drops from up to one polling interval to milliseconds; measured
  on PostgreSQL 17 with an in-memory publisher, a 2000-row backlog drains at about 1100 msg/s
  (`max-per-key=1`) and 1490 msg/s (`max-per-key=10`). With a real broker the rate is bounded by
  the acknowledgement latency, because sends stay sequential per instance.
- Because the outbox JSON mapper now follows the application's Jackson setup, an application-wide
  naming strategy renames envelope fields on the wire unless the application defines its own
  `outboxJsonMapper`.
- Audit expressions that call methods (`#order.getId()`) must be rewritten as property reads
  (`#order.id`).
