# Changelog — sdk-event-messaging-core

## 9.1.0

### Added
- `OutboxDispatchStore#backlog()` and `OutboxBacklog`: the messages of the store's origin still
  waiting (PENDING or IN_FLIGHT) and when the oldest was written. A default method returning empty,
  so other implementations keep compiling.
- `SendOutboxMessagesUseCase#stop()`: a running batch ends after the message being sent and
  releases the rest unsent; later runs claim nothing.
- A startup check for an outbox that is written to but never dispatched (an application
  `OutboxWriter` made the JDBC store back off, or no transport registered a publisher). It warns,
  or fails startup when `codehunters.outbox.publisher.type` is set.

## 9.0.0

Migration guide: [MIGRATION-9.0.md](../MIGRATION-9.0.md).

### Removed
- The pre-6.0 `outbox.*` property aliases: `OutboxLegacyProperties`, its `spring.factories` entry
  and the deprecated metadata entries. Only `codehunters.outbox.*` binds; an old key is ignored.
- `codehunters.outbox.claim.dialect` and `EventMessagingProperties.Claim#dialect()`: the outbox
  supports PostgreSQL only.
- The dependency on `sdk-support`.
- `OutboxRepositoryPort`: split into `OutboxWriter` and `OutboxDispatchStore`.
- The positional factory methods `OutboxEventFactory.create(topic, traceId, aggregate, eventName,
  eventType, payload)` and both `createRoutingKey(...)`, the `EventMessageFactory.create(traceId,
  [locale,] ...)` overloads, and the constructors `OutboxEventFactory(origin, ...)`,
  `EventMessageFactory(origin)` and `EventMessageFactory(origin, traceIdSource)`.
- The Lombok getters, setters and builders of `EventMessage`, `Header`, `Body` and
  `OutboxMessage`, and Lombok from the build.
- The nine- and ten-argument `SendOutboxMessagesUseCase` constructors.
- `codehunters.outbox.claim.ttl-ms`, `backoff.base-delay-ms` and `backoff.max-delay-ms`, with
  `Claim#ttlMs()`, `Backoff#baseDelayMs()` and `Backoff#maxDelayMs()`. No aliases: startup fails
  while an old key is set, in any relaxed form, naming the new key, and the metadata marks the old
  keys deprecated at error level.
- The `@ConditionalOnMissingBean(Clock.class)` outbox clock: an application `Clock` no longer
  replaces it.

### Changed
- `EventMessage`, `EventMessage.Header`, `EventMessage.Body` and `OutboxMessage` are immutable
  records with accessors (`id()`, `header().traceId()`). `Header.timestamp` is an `Instant` (was a
  `String`; still an ISO-8601 string on the wire) and the `OutboxMessage` times are `Instant`s (were
  UTC `LocalDateTime`s). `OutboxMessage` keeps `builder()` / `toBuilder()` and gains state
  transitions (`sendStarted`, `published`, `attemptFailed`, `retryAt`, `failed`, `released`).
- `OutboxEventFactory.create(OutboxEvent)` and `EventMessageFactory.create(OutboxEvent)` replace the
  positional methods; `new OutboxEventFactory(eventMessageFactory)`,
  `new EventMessageFactory(origin, traceIdSource, clock)`.
- `SendOutboxMessagesUseCase(OutboxDispatchStore, MessagePublisherPort, DispatchSettings)`, and
  `SendOutboxMessagesPort.execute()` returns `boolean`: `true` when the claim came back full.
- `OutboxDispatchStore.claim(ClaimRequest)` replaces `claimPendingMessages(origin, batchSize,
  claimedBy, claimTtl)`; `markSendStarted` returns `Optional<OutboxMessage<String>>` (was
  `boolean`); `purgePublished` takes an `Instant` cutoff.
- Every timing property is a `Duration`: `claim.ttl` (`5m`), `backoff.base-delay` (`30s`),
  `backoff.max-delay` (`1h`), `task.fixed-delay` (`5s`), `task.lock-at-most` (`5m`),
  `task.lock-at-least`, `purge.fixed-delay` (`1h`), `purge.initial-delay` (`1m`),
  `purge.lock-at-most` (`10m`), `purge.lock-at-least` (`30s`). A plain number still means
  milliseconds. Lock windows are validated (lock-at-least ≤ lock-at-most).
- The outbox clock is the bean `outboxClock` (`Clock.systemUTC()`, not a default candidate),
  injected by qualifier everywhere; define a bean with that name to replace it.

### Added
- `OutboxMessage.traceparent`: the W3C trace context of the work that created the row.
  `OutboxEventFactory` stamps it from `TraceIdSource.currentTraceParent()` (new default method,
  empty by default; the `sdk-tracing` source returns `TraceContextPort.currentTraceParent()`).
- `MessagePublisherPort.TRACEPARENT_HEADER` (`traceparent`) and `LEGACY_TRACEPARENT_HEADER`
  (`x-traceparent`), set by the transport adapters from the stored context.
- `codehunters.outbox.publisher.legacy-traceparent-header` (default `false`): also send the stored
  context as `x-traceparent`.
- `io.codehunters.messaging.tracing.TracedMessagePublisher` and the `outboxTraceContextDecorator`
  bean (when a Micrometer `Tracer` bean exists): each publish runs with the stored context as the
  current one, so observations started there parent on the request instead of the scheduled run.
- `io.codehunters.messaging.W3cTraceParent`: the one `traceparent` check the factory, the store,
  the transports and `TracedMessagePublisher` share (exactly 55 lowercase-hex characters, version
  not `ff`, trace and span ids not all zeros). `parse`, `isValid`, `validOrNull`, `sampled`,
  `value`.
- `OutboxEvent`: an immutable event description built in stages,
  `OutboxEvent.to(topic).key(key).type(name).payload(payload)`, with copy methods `routingKey`,
  `eventType`, `locale`, `traceId` and `schemaVersion`.
- `OutboxWriter` (`save`) for applications and `OutboxDispatchStore` (`claim`, `markSendStarted`,
  `update`, `purgePublished`, `countFailed`, `requeue(ids)`, `requeueFailed(topic, key)`) for the
  dispatcher, the purge and operators; `ClaimRequest(origin, claimedBy, batchSize, claimTtl,
  maxPerKey, commitGrace)`, with a five-argument constructor for no grace.
- `dispatch.RetryPolicy(maxAttempts, baseDelay, maxDelay)` and
  `dispatch.DispatchSettings(claim, retry, clock)`.
- `OutboxDispatchTrigger` (with `none()`) and `dispatch.CoalescingDispatchTrigger`, which runs a
  dispatch on an executor, coalesces concurrent requests and reruns while the claim comes back full.
  `stop()` drops later requests and ends a running drain after its current run; an interrupted
  drain thread ends it too.
- `codehunters.outbox.claim.commit-grace` (default `0s`): a row with a key is not claimed until that
  long after its `occurred_at`, so an earlier row of its key committed late is claimed first.
- `EventMessagingProperties#maxDispatchRun()`: half the claim TTL plus one transport timeout (the
  longer one while `publisher.type` is unset).
- `codehunters.outbox.claim.max-per-key` (default `10`, ≥ 1): rows of one `(topic, key)` claimed
  together, in order. The use case releases a key's later rows unsent once one of its rows is not
  published.
- `codehunters.outbox.scheduling.dispatch-on-commit` (default `true`), read by `-jdbc`.
- `EventMessage.Header.DEFAULT_VERSION` (`1.0.0`).

### Fixed
- A publish interrupted by shutdown was charged as a failed attempt with backoff. The row is now
  released to `PENDING` unchanged, the interrupt flag is kept, and the rest of the batch is
  released unsent.
- A failed write-back (`store.update`) ended the whole dispatch run. It is now logged by exception
  class, the row stays `IN_FLIGHT` for a reclaim after the TTL, the later rows of its key are
  released, and the other keys go on.
- Outbox messages lost the request's trace: only the trace id reached the envelope, and the
  dispatch published under the scheduler's context. The full context is now stored and continued.
- `codehunters.outbox.task.lock-name` and `purge.lock-name` defaulted to an unresolved placeholder
  (`outbox-${spring.application.name:unknown}`). They now have no default, and the tasks use
  `outbox-<application>` / `outbox-purge-<application>` when unset.

### Behaviour changes
- With Micrometer Tracing present, logs written while a row is published carry the trace id of the
  request that wrote it, not the scheduled dispatch's.
- `OutboxEventFactory` stores no `traceparent` when the current context is not a valid
  `W3cTraceParent`, and matches an explicit trace id with `equals` on the parsed trace id: a caller
  trace id that only equals the span id no longer stores the context.
- `TracedMessagePublisher` publishes a row whose stored context is version `ff` or longer than 55
  characters in the current context instead of continuing it.
- Without a Micrometer tracer bridge, the envelope `traceId` and the stored `traceparent` are empty:
  `sdk-tracing` 3.0 no longer writes the trace into the MDC its fallback port reads.
- The envelope locale no longer defaults to `"es"`: it is null unless `OutboxEvent.locale(...)` sets
  it.
- `task.lock-at-least` defaults to `0` (was `10000` ms), so a poll can start again right after the
  previous one.
- A claim may hold several rows of one key (`claim.max-per-key`); `1` keeps the 8.x behaviour.
- An application `Clock` bean no longer drives outbox timestamps, backoff or purge cut-offs.
- Per-key ordering assumes one writer per key at a time: `seq` is assigned at insert, so of two
  concurrent transactions writing one key, the earlier insert can commit, and be published, last.
  Lock the aggregate row in the writing transaction, or set `claim.commit-grace`.

## 8.0.0

Migration guide: [MIGRATION-8.0.md](../MIGRATION-8.0.md).

### Breaking
- Packages reorganised by capability: the model, ports, `MessagePublishException` and
  `MessagePublisherDecorator` move to `io.codehunters.messaging`; factories to
  `io.codehunters.messaging.factory`; `SendOutboxMessagesUseCase` to
  `io.codehunters.messaging.dispatch`; `MeteredMessagePublisher` to
  `io.codehunters.messaging.metrics`. Only imports change.

### Changed
- The old `outbox.*` keys are honoured until 9.0 (were due to go in 8.0).

## 7.0.0

Migration guide: [MIGRATION-7.0.md](../MIGRATION-7.0.md).

### Breaking
- `OutboxRepositoryPort.markSendStarted(OutboxMessage<String>)` is new: implementations write the
  send mark while they hold the claim and report whether they did
  ([ADR 0018](../../docs/adr/0018-charge-attempts-only-for-started-sends.md)).

### Changed
- `SendOutboxMessagesUseCase` marks each row right before publishing it. A row whose mark is
  refused (its claim was taken over) is neither published nor updated, and a mark that fails skips
  only that row instead of aborting the batch.
- `OutboxMessage` has a `sendStartedAt` property.
- The old `outbox.*` keys are honoured until 8.0 (were due to go in 7.0).

### Fixed
- A reclaim after a crash or stall no longer adds an attempt to rows of the abandoned batch that
  were never sent.

## 6.0.0

First release of the split artifacts; replaces `sdk-event-messaging` 5.1.5. Migration guide:
[MIGRATION-6.0.md](../MIGRATION-6.0.md).

### Breaking
- `sdk-event-messaging` is replaced by `-core` plus `-jdbc` and `-rabbit` or `-kafka`
  ([ADR 0001](../../docs/adr/0001-split-event-messaging-by-transport.md)). Packages are unchanged.
  `-core` declares the `io.codehunters:sdk-event-messaging` capability, so Gradle refuses 5.x and
  6.x on one classpath.
- Properties `outbox.*` → `codehunters.outbox.*`. Old keys (files and environment variables) are
  honoured until 7.0 with a deprecation warning.
- Old keys are read the way Boot binds them, so an environment variable may spell a dashed name
  with or without underscores (`OUTBOX_BATCH_SIZE` or `OUTBOX_BATCHSIZE`). Only the documented old
  keys are aliased, and startup lists old keys ignored because the new key is set.
- Invalid configuration fails startup naming the key: sizes and attempts below 1, non-positive
  durations, unknown `publisher.type`, and `claim.ttl-ms` not above `batch-size` times the transport
  timeout.
- `publisher.kafka.send-timeout` defaults to `5s` (was `10s`).
- `publisher.type` no longer defaults to `rabbit`; the transport artifact on the classpath decides,
  and with both present an unset type fails startup. The type is matched case-insensitively.
- `MessagePublishException` carries no broker text; errors are stored and logged as exception class
  names only.

### Added
- `TraceIdSource`: events created with a null or blank trace id take the current trace from
  `sdk-tracing`'s `TraceContextPort` when present.
- `outbox.publish` timer (`outcome`, `exception` tags) when Micrometer and a `MeterRegistry` exist.
- `MessagePublisherDecorator` extension point, applied to the dispatch publisher in bean order.
- `@ConditionalOnOutboxTransport` for transport auto-configurations.
- `MessagePublisherPort.publish(OutboxMessage)` (keyed publish) and the shared
  `OUTBOX_ID_HEADER` (`outbox-message-id`) consumers deduplicate on.
- `publisher.rabbit.confirm-timeout` (default `5s`) and the claim-budget check for both transports.
- Dispatch-lock check: with scheduling on, `task.lock-at-most` (now `300000` by default) must exceed
  `claim.ttl-ms / 2` plus the transport timeout, or startup fails naming the keys.
- Injectable `Clock` for every timestamp the use case writes.
- `scheduling.enabled` (default `true`), read by `-jdbc` to leave dispatch and purge scheduling to
  the application.
- ArchUnit rules keeping `domain` and `application` free of Spring, JDBC, AMQP and Kafka types.

### Fixed
- A message reclaimed after dispatcher crashes with `attempts` at `max-attempts` was published
  again; it is now marked `FAILED` (`max-attempts-exceeded`).
- A long batch could outlive its claim and be reclaimed by another instance while still being sent;
  publishing now stops at half the claim TTL and releases the rest of the batch to `PENDING`.
- A transport timeout large enough to overflow `batch-size × timeout` wrapped to a small or negative
  budget and passed the claim-budget check; it now fails startup naming the key.
