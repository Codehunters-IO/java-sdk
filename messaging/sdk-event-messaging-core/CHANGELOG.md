# Changelog — sdk-event-messaging-core

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
