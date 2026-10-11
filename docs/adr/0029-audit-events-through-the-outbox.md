# 0029 — Audit events through the transactional outbox

- Status: accepted
- Date: 2026-10-11

## Context

`sdk-audit-log` 5.0 writes every event to an `audit_event` table of the service's own database,
by default on a bounded executor after the caller's transaction completes. Services that already
publish domain events through the `sdk-event-messaging` outbox want the audit trail on the same
path: one durable, ordered stream that a central consumer reads, with no second table per service
and no events lost from an in-memory queue when the process dies. An audit row in another
transaction than the business write can also disagree with what was committed.

## Decision

- A new property `codehunters.audit.sink` selects the sink: `jdbc` (default, unchanged 5.0
  behavior) or `outbox`. Any other value fails startup. With `outbox`, the JDBC publisher, its
  executor and its JSON mapper are not created.
- `OutboxAuditEventPublisher` implements `AuditEventPublisherPort` over `OutboxWriter`. It writes
  synchronously on the caller's thread, inside the caller's transaction, for successful and failed
  calls alike: no `@Async`, no `REQUIRES_NEW`. Writer failures propagate. `publishDetached`
  (completion events of asynchronous calls) suspends the bound transaction (`NOT_SUPPORTED`) when a
  single or primary transaction manager exists, and writes directly otherwise.
- The wire payload is the `AuditEvent` itself inside the `EventMessage` envelope, sanitized: IP
  address, user agent and response summary are dropped, and the error message is kept only as an
  exception class name or `ROLLED_BACK` / `UNKNOWN`. The event name is the action (`AUDIT` when
  blank), `schemaVersion` is `1.0.0`, and the destination is `codehunters.audit.outbox.topic`
  (required, not blank).
- The outbox message key is the entity id, so the events of one entity are dispatched in order.
  Events without an entity id are written without a key (the staged builder needs one, so they are
  built with the placeholder `unkeyed`, which the envelope `aggregate` keeps) and never block the
  rows of another key.
- `sdk-event-messaging-core` is a `compileOnly` dependency, confined to a nested
  `@ConditionalOnClass` configuration. With `sink=outbox` but no messaging SDK on the classpath, or
  no `OutboxWriter` bean, startup fails with a message naming `sdk-event-messaging-jdbc` or
  `codehunters.outbox.enabled`. An application-defined `AuditEventPublisherPort` still wins.

## Consequences

- Good: the audit trail records exactly what committed, travels with the business events through
  the outbox's retries, ordering and trace context, and no event waits in an in-memory queue.
- Good: the 5.0 behavior is the default; the change is additive and ships as a minor version.
- Good: personal data that the JDBC sink keeps in a private table never reaches the broker.
- Bad: with `sink=outbox`, events of `@Auditable` calls that fail roll back together with the
  caller's transaction, so failed attempts are not audited; services that need them keep the JDBC
  sink.
- Bad: a failing outbox write fails the business call, since the write is synchronous.
- Bad: `sdk-audit-log` is now coupled at compile time to `sdk-event-messaging-core`'s API
  (`compileOnly`); a breaking change there needs a new `sdk-audit-log` release, and the versions
  must stay aligned through `sdk-bom`.
