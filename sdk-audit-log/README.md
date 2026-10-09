# sdk-audit-log

Declarative audit trail: annotate a method with `@Auditable` and every call, successful or not,
becomes an `AuditEvent` handed to an `AuditEventPublisherPort`. With a `DataSource` on the
context, the SDK writes the events to an `audit_event` table off the caller's thread.

```kotlin
implementation(platform("io.codehunters:sdk-bom:<version>"))
implementation("io.codehunters:sdk-audit-log")
```

API types live in `io.codehunters.audit`; auto-configuration in `io.codehunters.audit.config` and the
JDBC adapter and publishers in `io.codehunters.audit.persistence`. Coming from 3.x:
[MIGRATION-4.0.md](MIGRATION-4.0.md); from 4.x: [MIGRATION-5.0.md](MIGRATION-5.0.md).

## Usage

```java
@Auditable(
    entityType = "ORDER",
    action = "CREATE",
    entityId = "#result?.id",
    correlationId = "#command.requestId",
    payload = {"amount=#command.amount", "customer=#command.customerId"})
public Order createOrder(CreateOrderCommand command) { ... }
```

SpEL expressions see the parameters by name (`#command`, needs `-parameters`), by position (`#p0`,
`#a0`, `#arg0`) and the return value (`#result`). `#result` is reserved: it is `null` when the
method throws, even if a parameter is called `result`; write `#result?.id` so the failure event
records `null` cleanly.

Expressions run in a read-only sandbox (SpEL's `SimpleEvaluationContext`): they read properties,
through getters or record components (`#order.id`, `#order.customer.name`), index lists and maps
(`#items[0]`, `#tags['k']`, `#result?.['id']`) and use operators (`?:`, `?.`, comparisons,
arithmetic). They call no method (`#order.getId()`, `#map.get('k')`), reference no type
(`T(...)`), call no constructor (`new ...`), resolve no bean (`@name`) and assign nothing. Startup
reports an expression that does any of these, as it does one that does not parse; at runtime it
records `null`.

An expression that fails to evaluate, or reads a property whose getter throws, leaves its field
`null`; the event is still published, and the first failure of each method (overloads
apart) and attribute is logged at `WARN` with the exception class or SpEL message code, never the
message or the values. A `#result` expression that fails only because the method threw is not
warned about. Expressions are parsed at startup; see
[Startup checks](#startup-checks). A `correlationId` that is not a UUID is recorded as `null`.

Declare an `AuditContextProvider` bean to fill the actor, IP, user agent, HTTP method, resource
and source service from the security context or the request. Values set on the annotation win. A
provider method that throws leaves its field `null`.

Without one, the default provider fills nothing, unless [`sdk-web`](../sdk-web/README.md#device--iocodehunterswebdevice)
(its device part) is on the classpath of a servlet application: then it records the IP of the current request, resolved
behind the device SDK's trusted proxies, and its sanitized `User-Agent`. The rest stay empty;
declare your own provider to fill them.

When neither the annotation nor the provider names an actor and Spring Security is on the
classpath, the actor is the authenticated, non-anonymous user of the calling thread's
`SecurityContextHolder` (`Authentication.getName()`), where a servlet application keeps it (since
5.0.0). It is read only while a request is bound to the thread (Spring Web's
`RequestContextHolder`): a scheduler, listener or pooled thread can inherit a context
(`MODE_INHERITABLETHREADLOCAL`) or keep one that leaked, and would record a plausible but wrong
user, so calls there record no actor unless the provider or annotation names one. In WebFlux it
comes from the subscriber's Reactor context instead; see below. Spring
Security stays optional.

The event is recorded when the method returns. A method returning a `CompletionStage` (such as
`CompletableFuture`), or a Reactor `Mono` or `Flux`, is recorded when that work finishes instead,
with its outcome and its duration. `#result` is the stage's value or the `Mono`'s element (`null`
for a `Flux`).

- The stage is returned to the caller unchanged. Its duration runs from the method call.
- A `Mono` or `Flux` is returned decorated. Each subscription records one event and its duration
  runs from the subscription, so `retry(3)` or a second `subscribe()` records one event per
  attempt. Reactor is optional.
- A cancelled future, or a cancelled subscription (`next()`, `take(n)`, `timeout(...)`, a
  client that disconnects), is recorded with status `CANCELLED`, `success=false` and
  `errorMessage=java.util.concurrent.CancellationException`.
- A method declared to return a subclass such as `ConnectableFlux`, and other asynchronous types,
  are recorded when the method returns.
- The actor, IP and other `AuditContextProvider` fields, the MDC and every expression that does
  not name `#result` are taken on the calling thread when the method returns. Only `#result`
  expressions wait for the outcome. A provider backed by `SecurityContextHolder` or
  `RequestContextHolder` therefore works even when the work completes on a pool thread.
- In WebFlux the user is not on the calling thread: it travels in the subscriber's Reactor
  context. When Spring Security is on the classpath and neither the annotation nor the provider
  names an actor, a `Mono` or `Flux` event takes the authenticated, non-anonymous user that
  `ReactiveSecurityContextHolder` holds for that subscription (since 3.1.0). The annotation's
  `actorId` and the provider still win.
- The event is written detached from any transaction on the thread that completes the work
  (`AuditEventPublisherPort#publishDetached`), so completing a future inside another
  transaction neither joins nor waits for it. A completion signalled on a non-blocking thread,
  such as a Netty event loop, is written on Reactor's `boundedElastic` scheduler, never on the
  event loop.
- A failure to write it is logged, even with `fail-on-error=true`: the caller already has its
  result.

### What is recorded

- `status` is `SUCCESS`, `ERROR` or `CANCELLED`, `success` the same as a boolean. An event whose transaction
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
several and no primary, or none, there is no publisher and nothing is audited; startup says so
(see [Startup checks](#startup-checks)).

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
need them. Define a bean with either name to replace it. `auditJsonMapper` is built from Boot's
`JsonMapper.Builder` when the Jackson auto-configuration provides one, so `spring.jackson.*`,
your modules and your `JsonMapperBuilderCustomizer`s shape payloads as they shape your API; it is
its own instance, never your `JsonMapper`. Without Boot's builder it uses Jackson's defaults.

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
`JtaTransactionManager`, the SDK logs a warning at startup: mark it as primary. It also warns when
the only transaction manager is a `JtaTransactionManager` without a JTA `TransactionManager`,
which cannot suspend a transaction: configure the `TransactionManager` on it.

With JTA, then:

| Transaction managers | Write of a failed call |
|---|---|
| One (or primary) `JtaTransactionManager` with a JTA `TransactionManager` | Suspends the global transaction, commits in its own, resumes it: survives the rollback |
| One `JtaTransactionManager` without a JTA `TransactionManager` | Cannot suspend: the write fails and the event is lost (startup warning) |
| Several, a `JtaTransactionManager` among them, none primary | Auto-commit connection that JTA enlists: rolls back with the caller (startup warning) |

When a commit fails, Spring reports the outcome as unknown, so such a call is recorded as
`UNKNOWN`, never as `SUCCESS`.

This relies on the aspect running inside the transaction advice. Both default to the lowest
precedence, and Spring then applies the transaction advice first, which the Postgres tests check.
Keep `codehunters.audit.order` at its default, or higher than the order of
`@EnableTransactionManagement`. With `fail-on-error=true` and the aspect outside the transaction, a
call whose commit succeeded could still fail on the audit insert, and a client might retry it.

## Startup checks

Once every singleton exists, the SDK checks that `@Auditable` methods will actually be audited.
Each problem is logged at `ERROR`, or fails startup with `IllegalStateException` when
`codehunters.audit.startup-check.fail-fast=true`. `fail-on-error` does not make these checks fail
startup, so upgrading within 4.x never stops an application that starts today:

| Problem | Reported when |
|---|---|
| No `AuditEventPublisherPort` | A bean declares `@Auditable` methods, and you define no publisher while the JDBC one backs off: no `DataSource`, several `DataSource` beans with none `@Primary`, or Spring JDBC, Spring transactions or Jackson missing from the classpath. The message names the reason and one audited method |
| No `AuditEventPublisherPort` (excluded) | The same, when the JDBC auto-configuration is excluded or its classes are missing: the message says "missing classes or auto-configuration excluded" |
| No AspectJ weaver | A publisher exists but `org.aspectj:aspectjweaver` is not on the runtime classpath, so no AspectJ proxy is created |
| Invalid expression | An `entityId`, `actorId`, `correlationId` or `payload` expression cannot be parsed. The message names `Class#method(Params)`, the attribute (`payload[key]` for a payload entry) and the parser's error, never evaluated values |
| No AspectJ auto-proxy creator | A publisher exists but nothing applies the aspect (`spring.aop.auto=false` without `@EnableAspectJAutoProxy`). Logged at `WARN` |

Beans are inspected through their target class, so proxies are seen through; lazy beans are
inspected by type without being created. Publishers and `DataSource` beans of parent contexts
count. Like the aspect, only `@Auditable` on the class's own method counts: an annotation declared
only on an interface method is not audited and not reported. A factory bean is inspected as the
factory, not its product. With `codehunters.audit.enabled=false` no check runs.

## Database

PostgreSQL is the supported database. Create the table from the reference DDL shipped in the jar,
`db/migration/audit/postgresql/audit_event_create.sql`, copied into your own migrations. The script
has no Flyway version prefix, so the SDK never runs it. It needs PostgreSQL 13 or later for
`gen_random_uuid()`; on 12 or earlier run `CREATE EXTENSION IF NOT EXISTS pgcrypto;` first.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `codehunters.audit.enabled` | `true` | Turns the SDK off: no aspect, publisher, context provider or startup check |
| `codehunters.audit.fail-on-error` | `false` | Publish synchronously, inside the caller's transaction, and propagate failures |
| `codehunters.audit.startup-check.fail-fast` | `false` | A failed [startup check](#startup-checks) stops the application instead of logging an `ERROR` |
| `codehunters.audit.order` | `2147483647` | Precedence of the audit aspect; the lowest runs it inside `@Transactional` |
| `codehunters.audit.async.enabled` | `true` | Publish off the caller's thread |
| `codehunters.audit.async.core-pool-size` | `2` | Threads kept alive |
| `codehunters.audit.async.max-pool-size` | `4` | Thread ceiling once the queue is full |
| `codehunters.audit.async.queue-capacity` | `500` | Events waiting for a thread |
| `codehunters.audit.async.await-termination` | `10s` | How long shutdown waits for queued events |

The aspect is applied by Boot's AOP auto-configuration (`spring.aop.auto`, on by default with
AspectJ on the classpath); the SDK does not enable proxying itself. With `spring.aop.auto=false`,
enable AspectJ proxying in your configuration or `@Auditable` has no effect. Startup checks for
an AspectJ auto-proxy creator: without one it logs a warning, or fails when
`codehunters.audit.fail-on-error=true`. See [Startup checks](#startup-checks) for the others.

Invalid sizes fail at startup.

## Known limits

- Events still in the queue when the process is killed, or when `await-termination` runs out,
  are lost. Use `fail-on-error=true` where every event must be durable.
- An event held until its transaction completes is lost if the process dies before the
  transaction ends.
- Failures are logged with the exception class only, never its message.
- The completion event of a `CompletionStage`, `Mono` or `Flux` cannot fail the call, even with
  `fail-on-error=true`: the caller already has its result. A failed write is logged.
- If Reactor's `boundedElastic` scheduler is already shut down (application stopping), a
  completion signalled on a non-blocking thread is written on that thread.
- With `fail-on-error=true`, the cause of an `AuditPersistenceException` is redacted: it keeps
  the failure class, SQLState, vendor code and stack trace, never the database message, which can
  echo the row. Read them from `failureClass()`, `sqlState()` and `vendorCode()`.
- The startup check finds a refused construct only in expressions it can read: it cannot tell
  whether a property exists on the runtime type, so a misspelled property is still `null` at
  runtime, with one `WARN`.
