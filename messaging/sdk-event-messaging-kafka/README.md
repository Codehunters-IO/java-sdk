# sdk-event-messaging-kafka

Kafka publisher for the transactional outbox, through a dedicated idempotent producer, and the
consumer side of the envelope: a reader, non-blocking retry topics and dead letters without
exception messages ([Consuming envelopes](#consuming-envelopes)). Model and dispatch semantics are
described in
[`sdk-event-messaging-core`](../sdk-event-messaging-core/README.md).

```kotlin
implementation("io.codehunters:sdk-event-messaging-jdbc:9.3.0")
implementation("io.codehunters:sdk-event-messaging-kafka:9.3.0")
implementation("org.springframework.boot:spring-boot-starter-kafka")
```

`spring-kafka` is not brought transitively. The publisher is active when `spring-kafka` is on the
classpath, a `ProducerFactory` bean exists (Boot's Kafka auto-configuration provides one) and Kafka
is the outbox transport (this artifact alone, or `codehunters.outbox.publisher.type=kafka`).

## How it publishes

| Aspect | Behaviour |
|---|---|
| Producer | Its own `outboxKafkaProducerFactory` and `outboxKafkaTemplate`, derived from the application's `ProducerFactory` configuration (bootstrap servers, security, tuning). Neither is a default candidate |
| Source factory | The single `ProducerFactory` bean, or the `@Primary` one when there are several; several without a primary fail startup. A factory that does not expose its configuration (`getConfigurationProperties()` empty or unsupported) fails startup too: define `outboxKafkaProducerFactory` yourself |
| Customizers | Boot's `DefaultKafkaProducerFactoryCustomizer` beans (for example the Micrometer producer listener) are applied to the outbox factory, then the forced settings and String serializers are applied again so a customizer cannot change them |
| Client id | The application's `client.id` plus `-outbox`, or `outbox-${spring.application.name}` when unset, so JMX metric names and broker quotas do not collide with the application's producer |
| Forced settings | `StringSerializer` for key and value, `acks=all`, `enable.idempotence=true`; any `transactional.id` is dropped |
| Idempotence guards | `max.in.flight.requests.per.connection` above 5 is capped at 5 and `retries=0` is reset to Kafka's default, since idempotence rejects both. The derived configuration is parsed at startup, so any other invalid producer setting fails startup naming it, not the first dispatch |
| Timeouts | `max.block.ms`, `delivery.timeout.ms` and `request.timeout.ms` = `send-timeout`, `linger.ms=0` (see below) |
| Destination | Topic = outbox `topic`; the outbox `routingKey` is ignored |
| Key | Outbox `key` (the event aggregate), so one aggregate's events share a partition and keep order |
| Value | The stored JSON payload, sent as is; an application JSON serializer can no longer double-encode it |
| Headers | `outbox-message-id` = outbox row id, plus a JSON content type |
| Send | Blocks for metadata, buffer space and the broker acknowledgement together for at most `send-timeout` |
| Trace | `traceparent` record header = the context stored with the row (`OutboxMessage.traceparent`), sent unchanged so the consumer continues the request's trace, not the dispatcher's; `x-traceparent` too when `publisher.legacy-traceparent-header=true`. No header when the row has none. The outbox template does not enable Boot's observation |

Why those timeouts: `KafkaTemplate.send()` can itself block for `max.block.ms` (default 60 s) while
it fetches metadata or waits for buffer space, before the adapter starts waiting for the
acknowledgement, so a single publish could outlast the claim budget. The outbox producer therefore
sets `max.block.ms` to `send-timeout`, and the adapter waits for the acknowledgement only for what
is left of `send-timeout`. `delivery.timeout.ms` and `request.timeout.ms` equal `send-timeout` so the
producer gives up on a record when the adapter does; `linger.ms` is `0` because the dispatcher sends
one record at a time and waits for it, so lingering only adds latency. These values always satisfy
Kafka's `delivery.timeout.ms >= linger.ms + request.timeout.ms`. Values the application sets for
these four keys are replaced for outbox records only.

Outbox sends run outside Kafka transactions. Exactly-once comes from the consumer, not the producer:
the idempotent producer only removes duplicates caused by its own retries within one session.

## Failure semantics

| Situation | Result |
|---|---|
| Broker error or unreachable, or no metadata or acknowledgement within `send-timeout` | `MessagePublishException`; retried with backoff, `FAILED` after `max-attempts` |
| Acknowledgement lost after the broker wrote the record | Sent again on retry: delivery is at-least-once |

Consumers must deduplicate on the envelope's `header.eventId` (the outbox row id since 9.3), or the
`outbox-message-id` header for envelopes written before 9.3; `ReceivedEnvelope#eventId()` does both.

## Consuming envelopes

Since 9.3 the `io.codehunters.messaging.kafka.consumer` package reads the envelopes the outbox
publishes and handles failed records. It works with any `@KafkaListener` on a String-valued
consumer; nothing here needs an outbox.

### Reading

```java
@KafkaListener(topics = "orders.commands")
void on(ConsumerRecord<String, String> record) {
  ReceivedEnvelope<PlaceOrder> envelope = envelopeReader.read(record, PlaceOrder.class);
  if (processed.contains(envelope.eventId())) {
    return; // at-least-once delivery: a repeat
  }
  handle(envelope.payload(), envelope.idempotencyKey());
}
```

| Accessor | Value |
|---|---|
| `eventId()` | `header.eventId`, or the `outbox-message-id` header for envelopes written before 9.3; deduplicate on it |
| `idempotencyKey()` | The producer's business key, or null |
| `payload()`, `schemaVersion()`, `message()` | The payload, its schema version, the whole envelope |
| `traceparent()` | The `traceparent` record header, or null |
| `attempt()` | 1 on the main topic, then 2, 3, ... on each retry topic |

The reader uses `inboundEnvelopeJsonMapper`, built from the application's `JsonMapper.Builder` (so
payload settings and modules apply) with the envelope pinned by `EnvelopeFormat` and unknown fields
always ignored: fields added by newer producers never make an envelope unreadable. A value that is
missing, not JSON or not an envelope raises `UnreadableEnvelopeException`, whose message is fixed
and names the parser exception class only; the record value never reaches a log or a header.

### Retry topics

A failing listener should not stall its partition. Expose one `RetryTopicConfiguration` bean per
topic through `InboundRetryTopics`:

```java
@Bean
RetryTopicConfiguration ordersRetry(InboundRetryTopics retryTopics) {
  return retryTopics.configuration(
      new InboundRetry(
          "orders.commands",
          List.of(Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(5)),
          List.of(InvalidCommandException.class)));
}
```

| Aspect | Behaviour |
|---|---|
| Topology | `orders.commands` → `orders.commands-retry-0` (after 10 s) → `-retry-1` (1 min) → `-retry-2` (5 min) → `orders.commands.DLT`: 4 attempts. One retry topic per delay, suffixed by index, even when delays are equal |
| Not retried | `UnreadableEnvelopeException` and the listed exceptions (subclasses, and when found as a cause) go straight to `.DLT`. Anything else is retried |
| Topics | Never created by the SDK and the DLT handler is not started: provision every retry topic and the `.DLT` outside the service, **with the main topic's partition count**, since a failed record is republished on its own partition number |
| Forwarding | `inboundForwardingKafkaTemplate`, derived from the application's `ProducerFactory` like the outbox producer (String serializers, `acks=all`, idempotence, no transactional id, client id suffixed `-inbound-retry`). The value is forwarded byte for byte |
| Back-off | Scheduled on the SDK's `retryTopicSchedulerWrapper`, not on an application scheduler |

With `spring.kafka.listener.observation-enabled=true`, the listener on the main topic and on every
retry topic continues the producer's trace from the `traceparent` header, which the forwarding
template copies unchanged.

A blocking listener (one that waits on a slow call) must finish a poll's records within
`max.poll.interval.ms`; lower `max.poll.records` (for example to `1` to `10`) rather than raising the
interval, so a slow record does not get the consumer evicted from its group.

### Dead letters

`SanitizedDeadLetters` is the application's `RetryTopicComponentFactory`. On every hop (to a retry
topic or to `.DLT`) it forwards the value and headers unchanged and sets:

| Header | Value |
|---|---|
| `kafka_exception-fqcn`, `kafka_exception-cause-fqcn` | The listener's own exception class and its root cause class, names only |
| `kafka_original-topic`, `kafka_original-partition`, `kafka_original-offset`, `retry_topic-attempts` | Kept from Spring Kafka |
| `event-id` | The envelope's `eventId`, or `outbox-message-id`; replaced, not duplicated, on each hop |
| `trace-id` | The trace id of the `traceparent` header |

`kafka_exception-message`, `kafka_exception-stacktrace`, `kafka_dlt-exception-message` and
`kafka_dlt-exception-stacktrace` are never written, because exception messages and stack traces may
carry personal data ([ADR 0030](../../docs/adr/0030-inbound-envelope-ids-and-consumer-dead-letters.md)).
The listener exception is never logged; each dead-lettered record logs one WARN with its topic,
partition, offset and failure class. Nothing consumes the `.DLT`: inspect and replay it with your
own tooling.

### Dead-letter hooks

```java
@Bean
DeadLetterHook rejectOnDeadLetter(RejectionPublisher rejections) {
  // CommandRejections is the application's own port, for example one writing a rejection event
  return deadLetter -> rejections.reject(deadLetter.record(), deadLetter.failure().getClass());
}
```

Every `DeadLetterHook` bean runs, in bean order, on the consumer thread right before a record is
published to a topic ending in `.DLT`; never for a hop to a retry topic. A hook that throws fails
the recovery: nothing is published and the record is redelivered at the same hop, so no record
reaches the `.DLT` without its hooks having run. Delivery to hooks is therefore **at least once**:
make their side effects tolerate duplicates (key them on the event id). `DeadLetter#failure()` is the
listener's exception, whose message may carry personal data; keep its class name only.
`DeadLetter#record()` is the record as consumed, from the main topic or the last retry topic: the
`event-id` header is only there after a retry hop, so read the event id from the envelope with
`EnvelopeReader` (and expect `UnreadableEnvelopeException` for a record that never was one).

### Consumer configuration

| Key | Default | Notes |
|---|---|---|
| `codehunters.messaging.consumer.enabled` | `true` | `false` registers none of the consumer beans |

| Bean | Notes |
|---|---|
| `inboundEnvelopeJsonMapper` | Not a default candidate; define one with this name to replace it |
| `envelopeReader` | Backs off for an application `EnvelopeReader` |
| `retryTopicComponentFactory` (`SanitizedDeadLetters`), `retryTopicSchedulerWrapper` | With `spring-kafka`; back off for an application `RetryTopicComponentFactory` / `RetryTopicSchedulerWrapper` |
| `inboundForwardingProducerFactory`, `inboundForwardingKafkaTemplate` | With a `ProducerFactory` bean; not default candidates. Several factories without a `@Primary` fail startup: mark one primary or define `inboundForwardingProducerFactory` |
| `inboundRetryTopics` | With a `ProducerFactory` bean |

## Configuration

Keys under `codehunters.outbox`; the rest are in the [core README](../sdk-event-messaging-core/README.md).

| Key | Default | Notes |
|---|---|---|
| `publisher.type` | unset | Set to `kafka` only when `-rabbit` is also on the classpath |
| `publisher.kafka.send-timeout` | `5s` | > 0. Startup fails unless `claim.ttl > batch-size x send-timeout` (default 50 x 5 s < 300 s) |
| `publisher.legacy-traceparent-header` | `false` | `true` also sends the stored context as `x-traceparent` |

Producer settings come from `spring.kafka.producer.*` / `spring.kafka.*`, except the forced ones
and the timeouts above.

## Extension points

Define beans named `outboxKafkaProducerFactory` or `outboxKafkaTemplate` to replace the producer,
or a `MessagePublisherPort` to replace the publisher.

## Packages

| Package | Contents |
|---|---|
| `io.codehunters.messaging.kafka` | `KafkaMessagePublisherAdapter` |
| `io.codehunters.messaging.kafka.config` | Auto-configuration |
| `io.codehunters.messaging.kafka.consumer` | `EnvelopeReader`, `ReceivedEnvelope`, `UnreadableEnvelopeException`, `InboundRetry`, `InboundRetryTopics`, `SanitizedDeadLetters`, `DeadLetterHook`, `DeadLetter` |
| `io.codehunters.messaging.kafka.consumer.config` | Consumer auto-configuration |

## Upgrading from 9.2

Nothing to change: the consumer beans are new and the envelope gains two fields. See
[MIGRATION-9.0.md](../MIGRATION-9.0.md#upgrading-to-93).

## Migrating from 8.x

Each record now carries the `traceparent` stored with its row (add the `traceparent` column before
deploying); the family moves to PostgreSQL only and drops the old `outbox.*` keys. The adapter reads
`OutboxMessage` through record accessors, so a custom `MessagePublisherPort` must switch from
`getTopic()` to `topic()` and so on. Records are now sent right after their transaction commits, and
the outbox no longer turns on application-wide scheduling. See
[MIGRATION-9.0.md](../MIGRATION-9.0.md).

## Migrating from 7.x

Only imports change; see [MIGRATION-8.0.md](../MIGRATION-8.0.md), then
[MIGRATION-9.0.md](../MIGRATION-9.0.md).

## Migrating from 5.x

| 5.x | 6.0 |
|---|---|
| `sdk-event-messaging:5.x` with `outbox.publisher.type=kafka` | `sdk-event-messaging-jdbc` + `sdk-event-messaging-kafka` 6.0.0; the type is optional |
| Required a `KafkaTemplate` bean and sent through it | Requires a `ProducerFactory` bean; sends through `outboxKafkaTemplate` |
| Application value serializer applied to the payload | `StringSerializer`, payload sent as stored |
| `outbox.publisher.kafka.send-timeout` default `10s` | `codehunters.outbox.publisher.kafka.send-timeout` default `5s` |

If you raised `batch-size` or `send-timeout`, check that `claim.ttl` (`claim.ttl-ms` before 9.0)
still exceeds their product.
Full guide: [MIGRATION-6.0.md](../MIGRATION-6.0.md).
