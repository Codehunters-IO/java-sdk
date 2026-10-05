# Migrating from sdk-event-messaging 5.x to 6.0

6.0 splits `sdk-event-messaging` into four artifacts
([ADR 0001](../docs/adr/0001-split-event-messaging-by-transport.md)) and moves its configuration
under `codehunters.outbox` ([ADR 0004](../docs/adr/0004-single-codehunters-property-prefix.md)).
Java packages (`io.codehunters.messaging.*`) do not change.

Module docs: [core](sdk-event-messaging-core/README.md) · [jdbc](sdk-event-messaging-jdbc/README.md) ·
[rabbit](sdk-event-messaging-rabbit/README.md) · [kafka](sdk-event-messaging-kafka/README.md)

## Checklist

1. Apply the DDL changes (SQL Server: **before** deploying 6.0, see [DDL](#ddl)).
2. Replace the dependency.
3. Rename properties (optional until 7.0) and review the Kafka send timeout.
4. RabbitMQ: enable publisher confirms and returns.
5. Consumers: deduplicate on `outbox-message-id`.

## Coordinates

| 5.x | 6.0 |
|---|---|
| `io.codehunters:sdk-event-messaging:5.x` (RabbitMQ) | `sdk-event-messaging-jdbc:6.0.0` + `sdk-event-messaging-rabbit:6.0.0` |
| `io.codehunters:sdk-event-messaging:5.x` (Kafka) | `sdk-event-messaging-jdbc:6.0.0` + `sdk-event-messaging-kafka:6.0.0` |
| `io.codehunters:sdk-event-messaging:5.x` (only factories / model) | `sdk-event-messaging-core:6.0.0` |

`-jdbc`, `-rabbit` and `-kafka` bring `-core` transitively. `-core` declares the Gradle capability
`io.codehunters:sdk-event-messaging`, so a Gradle build that still resolves the 5.x jar fails with a
capability conflict instead of loading duplicate classes. Maven does not check capabilities: remove
the 5.x dependency yourself.

## Properties

Every key moves from `outbox.*` to `codehunters.outbox.*`. The old keys (in files and environment
variables) are still honoured until 7.0: each is copied to its new name when the new one is unset,
with one deprecation warning at startup.

| 5.x | 6.0 |
|---|---|
| `outbox.enabled` | `codehunters.outbox.enabled` |
| `outbox.batch-size` | `codehunters.outbox.batch-size` |
| `outbox.max-attempts` | `codehunters.outbox.max-attempts` |
| `outbox.task.*` | `codehunters.outbox.task.*` |
| `outbox.claim.*` | `codehunters.outbox.claim.*` |
| `outbox.backoff.*` | `codehunters.outbox.backoff.*` |
| `outbox.publisher.*` | `codehunters.outbox.publisher.*` |
| `outbox.purge.*` | `codehunters.outbox.purge.*` |

Changed behaviour:

| 5.x | 6.0 |
|---|---|
| `publisher.kafka.send-timeout` default `10s` | `5s`, so the default claim budget (50 x 5 s < 300 s) holds |
| `publisher.type` default `rabbit` | No default; the transport artifact decides (below) |
| Out-of-range values accepted | Startup fails naming the key (sizes and attempts ≥ 1, positive durations) |
| No claim budget check | Startup fails unless `claim.ttl-ms > batch-size x` the transport timeout |
| `task.lock-at-most` default `60000`, shorter than a dispatch run | `300000`; startup fails unless it exceeds `claim.ttl-ms / 2` + the transport timeout. A 5.x value of `60000` must be raised or removed |
| — | New: `publisher.rabbit.confirm-timeout` (default `5s`) |
| Scheduling always on with the outbox | New: `scheduling.enabled` (default `true`); `false` drops the dispatch and purge schedulers and `@EnableScheduling` |

## Transport selection

| Artifacts on the classpath | `codehunters.outbox.publisher.type` | Publisher |
|---|---|---|
| `-rabbit` only | unset | RabbitMQ |
| `-kafka` only | unset | Kafka |
| both | unset | startup fails: set the type |
| any | `rabbit` / `kafka` (case-insensitive) | the named one, if its artifact is present |

An explicit type whose artifact is missing leaves the application without a publisher: the
dispatch use case and scheduler are not created and nothing is sent.

## RabbitMQ

The outbox publishes through its own `outboxRabbitTemplate`, built from the application's
`ConnectionFactory`. That factory must enable confirms and returns, or startup fails:

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: simple # or correlated
    publisher-returns: true
```

Both `simple` and `correlated` confirms are accepted. The outbox template publishes on the factory's
publisher connection (`usePublisherConnection=true`), separate from the consumers' connection with
Boot's `CachingConnectionFactory`; count one more connection per instance against broker limits.

The SDK no longer defines a global `RabbitTemplate` or `JacksonJsonMessageConverter`. An
application that injected them must declare its own. Trace propagation on published messages is
done by `sdk-tracing`, which instruments every `RabbitTemplate`.

## Kafka

The outbox needs a `ProducerFactory` bean (Boot's `KafkaAutoConfiguration` provides one) instead of
a `KafkaTemplate`. It derives its own idempotent producer from it (String serializers, `acks=all`,
`enable.idempotence=true`, no transactional id). The application's `KafkaTemplate` is no longer
used for outbox records, so a JSON value serializer can no longer double-encode them.

The outbox producer also bounds every wait by `publisher.kafka.send-timeout`: `max.block.ms`,
`delivery.timeout.ms` and `request.timeout.ms` equal it and `linger.ms` is `0`, whatever
`spring.kafka.producer.*` says. The application's own producer keeps its settings.
`max.in.flight.requests.per.connection` above 5 is capped at 5 and `retries=0` is reset for the
outbox producer, and an invalid producer setting now fails startup instead of the first dispatch.

## DDL

The SDK never runs DDL. Copy the reference files from the `-jdbc` jar
(`db/migration/outbox/**`) into your own changelog.

| Database | Action |
|---|---|
| SQL Server, table created before 6.0 | Apply `sqlserver/outbox_message_add_seq.sql` **before** deploying 6.0: the claim orders by `seq` and fails without it |
| PostgreSQL | Compare with `postgresql/outbox_message_create.sql` (`seq` column and indexes) |
| New install | `postgresql/outbox_message_create.sql` or `outbox_message_create.sql` (SQL Server) |

Drain the outbox before adding `seq` if the relative order of rows already queued matters.

MySQL and MariaDB are not supported: `claim.dialect` `auto` or `generic` fails startup on them.

## Consumers

Delivery is at-least-once. A stale claim, a confirm timeout or a crash after the broker accepted
the message all lead to a second send. Both transports tag every outbox record with the
`outbox-message-id` header (the outbox row id); RabbitMQ also sets it as the AMQP `messageId`.
Consumers should record processed ids and skip repeats.
