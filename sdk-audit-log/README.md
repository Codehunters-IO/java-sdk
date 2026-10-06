# sdk-audit-log

Declarative audit trail: annotate a method with `@Auditable` and every call, successful or not,
becomes an `AuditEvent` handed to an `AuditEventPublisherPort`. With a `DataSource` on the
context, the SDK writes the events to an `audit_event` table off the caller's thread.

```kotlin
implementation(platform("io.codehunters:sdk-bom:<version>"))
implementation("io.codehunters:sdk-audit-log")
```

## Usage

```java
@Auditable(
    entityType = "ORDER",
    action = "CREATE",
    entityId = "#result.id",
    correlationId = "#command.requestId",
    payload = {"amount=#command.amount", "customer=#command.customerId"})
public Order createOrder(CreateOrderCommand command) { ... }
```

SpEL expressions see the parameters by name (`#command`, needs `-parameters`), by position (`#p0`,
`#a0`, `#arg0`) and the return value (`#result`, `null` when the method throws). An expression
that fails to parse or evaluate leaves its field `null`; the event is still published. A
`correlationId` that is not a UUID is recorded as `null`.

Declare an `AuditContextProvider` bean to fill the actor, IP, user agent, HTTP method, resource
and source service from the security context or the request. Values set on the annotation win.

### What is recorded

- `status` is `SUCCESS` or `ERROR`, `success` the same as a boolean.
- `errorMessage` holds the **class name** of the exception, never its message: messages often
  carry personal data or secrets.
- `durationMs` is measured with a monotonic clock. `createdAt` comes from the application's
  `Clock` bean when it defines exactly one, UTC otherwise.
- `payload` holds only the expressions listed in `payload`. Arguments are never captured whole.
  Do not list passwords, tokens or card numbers there.

## Publishing

| Setup | Behaviour |
|---|---|
| Your own `AuditEventPublisherPort` bean | Used as is; the SDK's JDBC publisher backs off |
| `DataSource`, default settings | Written to `audit_event` on `auditTaskExecutor`; the call does not wait |
| `codehunters.audit.async.enabled=false` | Written on the caller's thread |
| `codehunters.audit.fail-on-error=true` | Written on the caller's thread; a failed insert throws `AuditPersistenceException` |

Asynchronous publishing never fails the caller. The executor is bounded: when its queue is full,
or the application is shutting down, the event is rejected, logged at `WARN` and counted in the
`audit.events.rejected` Micrometer counter (when Micrometer is present). Declare an
`AuditEventRejectionListener` bean to handle rejections yourself. On shutdown the executor waits
up to `async.await-termination` for queued events, and is closed before the data source.

The worker runs with the caller's MDC, so `traceId` and the rest reach the audit log lines.

With `fail-on-error=true`, the insert joins the caller's Spring transaction on the same data
source, if there is one: the audit row commits or rolls back with the business write, including
the `ERROR` event of a call whose transaction rolls back. A failure to insert throws
`AuditPersistenceException` from the audited call; when the call itself failed, the original
exception is thrown with the audit failure attached as suppressed.

The `auditTaskExecutor` and `auditJsonMapper` beans are not autowiring candidates: they never
replace Boot's `applicationTaskExecutor` or your `JsonMapper`. Inject them with `@Qualifier` if you
need them. Define a bean with either name to replace it.

## Database

Create the table from the reference DDL shipped in the jar, copied into your own migrations:

| Database | Script |
|---|---|
| PostgreSQL | `db/migration/audit/postgresql/audit_event_create.sql` |
| SQL Server | `db/migration/audit/sqlserver/audit_event_create.sql` |

The scripts have no Flyway version prefix, so the SDK never runs them.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `codehunters.audit.fail-on-error` | `false` | Publish synchronously and propagate insert failures |
| `codehunters.audit.async.enabled` | `true` | Publish off the caller's thread |
| `codehunters.audit.async.core-pool-size` | `2` | Threads kept alive |
| `codehunters.audit.async.max-pool-size` | `4` | Thread ceiling once the queue is full |
| `codehunters.audit.async.queue-capacity` | `500` | Events waiting for a thread |
| `codehunters.audit.async.await-termination` | `10s` | How long shutdown waits for queued events |

Invalid sizes fail at startup. The 2.x key `vitxo.audit.fail-on-error` works until 4.0.0 and logs
a deprecation warning.

## Known limits

- Events still in the queue when the process is killed, or when `await-termination` runs out,
  are lost. Use `fail-on-error=true` where every event must be durable.
- The aspect has no explicit order: whether it runs inside or outside a `@Transactional` boundary
  on the same method follows Spring's default advisor ordering.
