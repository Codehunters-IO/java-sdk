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
`#a0`, `#arg0`) and the return value (`#result`). `#result` is reserved: it is `null` when the
method throws, even if a parameter is called `result`. An expression that fails to parse, fails to
evaluate, or calls a method that throws leaves its field `null`; the event is still published. A
`correlationId` that is not a UUID is recorded as `null`.

Declare an `AuditContextProvider` bean to fill the actor, IP, user agent, HTTP method, resource
and source service from the security context or the request. Values set on the annotation win. A
provider method that throws leaves its field `null`.

The event is recorded when the method returns. A method returning a `CompletionStage` (such as
`CompletableFuture`), or a Reactor `Mono` or `Flux`, is recorded when that work finishes instead,
with its outcome and its duration. `#result` is the stage's value or the `Mono`'s element (`null`
for a `Flux`).

- The stage is returned to the caller unchanged. Its duration runs from the method call.
- A `Mono` or `Flux` is returned decorated. Each subscription records one event, its duration
  runs from the subscription, and a cancelled subscription records none. Reactor is optional.
- Other asynchronous types are recorded when the method returns.
- The event is published on the thread that completes the work, outside the caller's transaction.
  A failure to write it is logged, even with `fail-on-error=true`: the caller already has its
  result.

### What is recorded

- `status` is `SUCCESS` or `ERROR`, `success` the same as a boolean. An event whose transaction
  did not commit is `ROLLED_BACK` (`success=false`) or `UNKNOWN` (`success` null); see
  [Transactions](#transactions).
- `errorMessage` holds the **class name** of the exception, never its message: messages often
  carry personal data or secrets. For `ROLLED_BACK` and `UNKNOWN` it holds that status.
- `durationMs` is measured with a monotonic clock. `createdAt` comes from the application's
  `Clock` bean when it defines exactly one, UTC otherwise.
- `payload` holds only the expressions listed in `payload`. Arguments are never captured whole.
  Do not list passwords, tokens or card numbers there. The values are copied to JSON when the
  method returns, on the caller's thread, so later changes to the objects and closed persistence
  sessions do not affect the record. A map that cannot be serialized is stored as `null` and a
  warning is logged; its `toString()` is never stored.

## Publishing

| Setup | Behaviour |
|---|---|
| Your own `AuditEventPublisherPort` bean | Used as is; the SDK's JDBC publisher backs off |
| One `DataSource`, default settings | Written to `audit_event` on `auditTaskExecutor`; the call does not wait |
| `codehunters.audit.async.enabled=false` | Written on the caller's thread |
| `codehunters.audit.fail-on-error=true` | Written on the caller's thread; a failure throws `AuditPersistenceException` |

The JDBC publisher needs a single `DataSource` candidate: one bean, or one marked `@Primary`. With
several and no primary, the SDK stays off and the application starts without it.

Without `fail-on-error`, publishing never fails the caller. The executor is bounded: when its
queue is full, or the application is shutting down, the event is rejected, logged at `WARN` and
counted in `audit.events.rejected`. An insert that fails is logged at `ERROR`, with the entity
type, action, SQLState and exception class but not the database message, which can echo the row,
and counted in `audit.events.failed`. Both counters are registered at startup when Micrometer and
a single `MeterRegistry` are present. Declare an `AuditEventRejectionListener` or
`AuditEventFailureListener` bean to handle these yourself. On shutdown the executor waits up to
`async.await-termination` for queued events, and is closed before the data source.

The worker runs with the caller's MDC, so `traceId` and the rest reach the audit log lines.

With `fail-on-error=true`, a failure to build or insert the event throws
`AuditPersistenceException` from the audited call. When the call itself failed, the original
exception is thrown with the audit failure attached as suppressed.

The `auditTaskExecutor` and `auditJsonMapper` beans are not autowiring candidates: they never
replace Boot's `applicationTaskExecutor` or your `JsonMapper`. Inject them with `@Qualifier` if you
need them. Define a bean with either name to replace it.

### Transactions

When the audited method runs in a Spring transaction, the audit row follows what was committed:

| Event | `fail-on-error=true` | Otherwise |
|---|---|---|
| The method threw | Written at once in a transaction of its own; survives the rollback | Same, on the executor when async |
| The method returned | Written inside the caller's transaction; commits or rolls back with it | Written after the transaction completes; `ROLLED_BACK` or `UNKNOWN` if it did not commit |

A transaction of its own means `REQUIRES_NEW` on the application's transaction manager when it has
exactly one (or a primary one). Otherwise the row is written on a separate auto-commit connection
from the same data source; that works for `DataSourceTransactionManager` and JPA, but a JTA
transaction would still enlist it. When one of several transaction managers is a
`JtaTransactionManager`, the SDK logs a warning at startup: mark it as primary.

When a commit fails, Spring reports the outcome as unknown, so such a call is recorded as
`UNKNOWN`, never as `SUCCESS`.

This relies on the aspect running inside the transaction advice. Both default to the lowest
precedence, and Spring then applies the transaction advice first, which the Postgres tests check.
Keep `codehunters.audit.order` at its default, or higher than the order of
`@EnableTransactionManagement`. With `fail-on-error=true` and the aspect outside the transaction, a
call whose commit succeeded could still fail on the audit insert, and a client might retry it.

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
| `codehunters.audit.fail-on-error` | `false` | Publish synchronously, inside the caller's transaction, and propagate failures |
| `codehunters.audit.order` | `2147483647` | Precedence of the audit aspect; the lowest runs it inside `@Transactional` |
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
- An event held until its transaction completes is lost if the process dies before the
  transaction ends.
- A cancelled `Mono` or `Flux` subscription is not recorded.
- With `fail-on-error=true`, the cause of an `AuditPersistenceException` is redacted: it keeps
  the failure class, SQLState, vendor code and stack trace, never the database message, which can
  echo the row. Read them from `failureClass()`, `sqlState()` and `vendorCode()`.
